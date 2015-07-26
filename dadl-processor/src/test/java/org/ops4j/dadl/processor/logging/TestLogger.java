package org.ops4j.dadl.processor.logging;

import org.jboss.logging.BasicLogger;
import org.jboss.logging.Logger;
import org.jboss.logging.Logger.Level;
import org.jboss.logging.annotations.LogMessage;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageLogger;

@MessageLogger(projectCode = "")
public interface TestLogger extends BasicLogger {

    TestLogger LOGGER = Logger.getMessageLogger(TestLogger.class, TestLogger.class.getName());

    @LogMessage(level = Level.DEBUG)
    @Message("Hello from interface!")
    void hello();
}
