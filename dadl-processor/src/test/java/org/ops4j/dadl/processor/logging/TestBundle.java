package org.ops4j.dadl.processor.logging;

import org.jboss.logging.Messages;
import org.jboss.logging.annotations.Message;
import org.jboss.logging.annotations.MessageBundle;

@MessageBundle(projectCode = "")
public interface TestBundle {

    TestBundle MESSAGES = Messages.getBundle(TestBundle.class);

    @Message("Hello from bundle!")
    String helloFromBundle();
}
