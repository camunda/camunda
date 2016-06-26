package org.camunda.tngp.client.impl.cmd;

import org.camunda.tngp.util.buffer.BufferWriter;

public interface ClientRequestWriter extends BufferWriter
{
    void validate();

}
