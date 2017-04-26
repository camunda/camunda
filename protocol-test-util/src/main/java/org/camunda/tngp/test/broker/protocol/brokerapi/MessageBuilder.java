package org.camunda.tngp.test.broker.protocol.brokerapi;

import org.camunda.tngp.util.buffer.BufferWriter;

public interface MessageBuilder<T> extends BufferWriter
{

    void initializeFrom(T context);

}
