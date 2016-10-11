package org.camunda.tngp.util.buffer;

import java.nio.ByteBuffer;

import org.agrona.DirectBuffer;

public interface PayloadRequestWriter extends RequestWriter
{

    void payload(byte[] payload, int offset, int length);

    void payload(DirectBuffer buffer, int offset, int length);

    void payload(ByteBuffer byteBuffer);
}
