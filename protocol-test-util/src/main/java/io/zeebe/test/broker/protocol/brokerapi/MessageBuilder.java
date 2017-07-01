package io.zeebe.test.broker.protocol.brokerapi;

import io.zeebe.util.buffer.BufferWriter;

public interface MessageBuilder<T> extends BufferWriter
{

    void initializeFrom(T context);

}
