import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.DDSpanTypes
import datadog.trace.bootstrap.instrumentation.api.Tags
import org.hibernate.Session
import org.hibernate.SessionFactory
import org.hibernate.cfg.Configuration
import org.hibernate.exception.SQLGrammarException
import org.hibernate.procedure.ProcedureCall
import spock.lang.Shared

import javax.persistence.ParameterMode
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement

import static datadog.trace.agent.test.utils.TraceUtils.runUnderTrace
import static datadog.trace.bootstrap.instrumentation.api.AgentTracer.activeSpan

class ProcedureCallTest extends AgentTestRunner {


  @Shared
  protected SessionFactory sessionFactory

  @Shared
  protected List<Value> prepopulated

  def setupSpec() {
    def setupSpan = runUnderTrace("setup") {
      sessionFactory = new Configuration().configure().buildSessionFactory()
      // Pre-populate the DB, so delete/update can be tested.
      Session writer = sessionFactory.openSession()
      writer.beginTransaction()
      prepopulated = new ArrayList<>()
      for (int i = 0; i < 2; i++) {
        prepopulated.add(new Value("Hello :) " + i))
        writer.save(prepopulated.get(i))
      }
      writer.getTransaction().commit()
      writer.close()

      // Create a stored procedure.
      Connection conn = DriverManager.getConnection("jdbc:hsqldb:mem:test", "sa", "1")
      Statement stmt = conn.createStatement()
      stmt.execute("CREATE PROCEDURE TEST_PROC() MODIFIES SQL DATA BEGIN ATOMIC INSERT INTO Value VALUES (420, 'fred'); END")
      stmt.close()
      conn.close()

      activeSpan()
    }
    TEST_WRITER.waitUntilReported(setupSpan)
  }

  def cleanupSpec() {
    if (sessionFactory != null) {
      sessionFactory.close()
    }
  }

  def "test ProcedureCall"() {
    setup:

    Session session = sessionFactory.openSession()
    session.beginTransaction()

    ProcedureCall call = session.createStoredProcedureCall("TEST_PROC")
    call.getOutputs()

    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(4) {
        span {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span {
          serviceName "hibernate"
          resourceName "TEST_PROC"
          operationName "hibernate.procedure.getOutputs"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span {
          serviceName "hsqldb"
          spanType "sql"
          childOf span(2)
          tags {
            "$Tags.COMPONENT" "java-jdbc-prepared_statement"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            "$Tags.DB_TYPE" "hsqldb"
            "$Tags.DB_INSTANCE" "test"
            "$Tags.DB_USER" "sa"
            "$Tags.DB_OPERATION" "call"
            defaultTags()
          }
        }
      }
    }
  }

  def "test failing ProcedureCall"() {
    setup:

    Session session = sessionFactory.openSession()
    session.beginTransaction()

    ProcedureCall call = session.createStoredProcedureCall("TEST_PROC")
    call.registerParameter("nonexistent", Long, ParameterMode.IN)
    call.getParameterRegistration("nonexistent").bindValue(420L)
    try {
      call.getOutputs()
    } catch (Exception e) {
      // We expected this.
    }

    session.getTransaction().commit()
    session.close()

    expect:
    assertTraces(1) {
      trace(3) {
        span {
          serviceName "hibernate"
          resourceName "hibernate.session"
          operationName "hibernate.session"
          spanType DDSpanTypes.HIBERNATE
          parent()
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span {
          serviceName "hibernate"
          resourceName "hibernate.transaction.commit"
          operationName "hibernate.transaction.commit"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            defaultTags()
          }
        }
        span {
          serviceName "hibernate"
          resourceName "TEST_PROC"
          operationName "hibernate.procedure.getOutputs"
          spanType DDSpanTypes.HIBERNATE
          childOf span(0)
          errored(true)
          tags {
            "$Tags.COMPONENT" "java-hibernate"
            "$Tags.SPAN_KIND" Tags.SPAN_KIND_CLIENT
            errorTags(SQLGrammarException, "could not prepare statement")
            defaultTags()
          }
        }
      }
    }
  }
}

