package org.camunda.tngp.test.broker.protocol.brokerapi;

import org.camunda.tngp.util.buffer.BufferWriter;

public interface ResponseStub<T> extends BufferWriter
{

    boolean applies(T request);

    void initiateFrom(T request);

}
