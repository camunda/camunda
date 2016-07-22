package org.camunda.tngp.client.impl.cmd;

import java.nio.ByteBuffer;

import uk.co.real_logic.agrona.DirectBuffer;

public interface PayloadRequestWriter extends ClientRequestWriter
{

    void payload(byte[] payload, int offset, int length);

    void payload(DirectBuffer buffer, int offset, int length);

    void payload(ByteBuffer byteBuffer);
}
