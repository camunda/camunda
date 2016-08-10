package org.camunda.tngp.broker.log;

import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.util.buffer.BufferWriter;

public interface ResponseControl
{
    void accept(BufferWriter responseWriter);

    void reject(ErrorWriter errorWriter);

}
