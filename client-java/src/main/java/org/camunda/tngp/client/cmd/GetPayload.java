package org.camunda.tngp.client.cmd;

import java.io.InputStream;
import java.nio.ByteBuffer;

import uk.co.real_logic.agrona.MutableDirectBuffer;

public interface GetPayload
{
    /**
     * @return the length (in bytes) of the payload.
     */
    int payloadLength();

    int putPayload(ByteBuffer buffer);

    int putPayload(MutableDirectBuffer buffer, int offset, int lenght);

    byte[] getPayloadBytes();

    String getPayloadString();

    InputStream getPayloadStream();
}
