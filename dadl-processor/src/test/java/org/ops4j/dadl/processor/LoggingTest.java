package org.ops4j.dadl.processor;

import org.junit.Test;
import org.ops4j.dadl.processor.logging.TestBundle;
import org.ops4j.dadl.processor.logging.TestLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class LoggingTest {

    private static Logger slf4jLogger = LoggerFactory.getLogger(LoggingTest.class);
    private static org.jboss.logging.Logger jbossLogger = org.jboss.logging.Logger.getLogger(LoggingTest.class);


    @Test
    public void shouldLogViaSlf4J() {
        String object = "SLF4J";
        slf4jLogger.info("Hello {}!", object);
    }

    @Test
    public void shouldLogViaJBossLogging() {
        String object = "world";
        jbossLogger.info("Hello JBoss Logging!");
        jbossLogger.infov("Hello {0}!", object);
    }

    @Test
    public void shouldLogViaMessageLogger() {
        TestLogger.LOGGER.hello();
    }

    @Test
    public void shouldGetMessageFromBundle() {
        slf4jLogger.info(TestBundle.MESSAGES.helloFromBundle());
    }
}
