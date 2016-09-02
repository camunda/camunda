package org.camunda.tngp.protocol.wf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.Test;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceRequestReaderTest
{
    protected static final byte[] RESOURCE = new byte[] {1, 1, 2, 3, 5, 8};

    public int writeSbeEncodedRequestBuffer(final MutableDirectBuffer buffer, final int offset, final byte[] resource)
    {
        final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        final DeployBpmnResourceEncoder sbeEncoder = new DeployBpmnResourceEncoder();

        headerEncoder.wrap(buffer, offset)
            .blockLength(sbeEncoder.sbeBlockLength())
            .resourceId(123)
            .schemaId(sbeEncoder.sbeSchemaId())
            .version(sbeEncoder.sbeSchemaVersion())
            .shardId(456)
            .templateId(sbeEncoder.sbeTemplateId());

        sbeEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .putResource(resource, 0, resource.length);

        return headerEncoder.encodedLength() + sbeEncoder.encodedLength();
    }

    @Test
    public void testReading()
    {
        // given
        final DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        final int encodedLength = writeSbeEncodedRequestBuffer(buffer, 0, RESOURCE);

        // when
        reader.wrap(buffer, 0, encodedLength);

        // then
        final DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(6);

        final byte[] resource = new byte[6];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 1, (byte) 1, (byte) 2,
                (byte) 3, (byte) 5, (byte) 8);
    }

    @Test
    public void testReadingWithOffset()
    {
        // given
        final DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        final int encodedLength = writeSbeEncodedRequestBuffer(buffer, 52, RESOURCE);

        // when
        reader.wrap(buffer, 52, encodedLength);

        // then
        final DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(6);

        final byte[] resource = new byte[6];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 1, (byte) 1, (byte) 2,
                (byte) 3, (byte) 5, (byte) 8);
    }

    @Test
    public void testReuseInstance()
    {
        // given two buffers
        final DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();

        final MutableDirectBuffer buffer1 = new UnsafeBuffer(new byte[512]);
        final int encodedLength1 = writeSbeEncodedRequestBuffer(buffer1, 0, RESOURCE);
        final MutableDirectBuffer buffer2 = new UnsafeBuffer(new byte[512]);
        final int encodedLength2 = writeSbeEncodedRequestBuffer(buffer2, 0, new byte[]{12, 123, 13});

        // and the first buffer has already been read
        reader.wrap(buffer1, 0, encodedLength1);
        reader.getResource();

        // when we read the second buffer
        reader.wrap(buffer2, 0, encodedLength2);

        // then the resource is returned correctly
        final DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(3);

        final byte[] resource = new byte[3];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 12, (byte) 123, (byte) 13);
    }
}
