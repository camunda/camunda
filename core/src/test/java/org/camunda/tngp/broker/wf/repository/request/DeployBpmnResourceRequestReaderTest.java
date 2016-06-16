package org.camunda.tngp.broker.wf.repository.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.protocol.wf.DeployBpmnResourceEncoder;
import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.junit.Test;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceRequestReaderTest
{
    protected static final byte[] RESOURCE = new byte[] {1, 1, 2, 3, 5, 8};

    public int writeSbeEncodedRequestBuffer(MutableDirectBuffer buffer, int offset, byte[] resource)
    {
        MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
        DeployBpmnResourceEncoder sbeEncoder = new DeployBpmnResourceEncoder();

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
        DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        int encodedLength = writeSbeEncodedRequestBuffer(buffer, 0, RESOURCE);

        // when
        reader.wrap(buffer, 0, encodedLength);

        // then
        DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(6);

        byte[] resource = new byte[6];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 1, (byte) 1, (byte) 2,
                (byte) 3, (byte) 5, (byte) 8);
    }

    @Test
    public void testReadingWithOffset()
    {
        // given
        DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();
        MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);
        int encodedLength = writeSbeEncodedRequestBuffer(buffer, 52, RESOURCE);

        // when
        reader.wrap(buffer, 52, encodedLength);

        // then
        DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(6);

        byte[] resource = new byte[6];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 1, (byte) 1, (byte) 2,
                (byte) 3, (byte) 5, (byte) 8);
    }

    @Test
    public void testReuseInstance()
    {
        // given two buffers
        DeployBpmnResourceRequestReader reader = new DeployBpmnResourceRequestReader();

        MutableDirectBuffer buffer1 = new UnsafeBuffer(new byte[512]);
        int encodedLength1 = writeSbeEncodedRequestBuffer(buffer1, 0, RESOURCE);
        MutableDirectBuffer buffer2 = new UnsafeBuffer(new byte[512]);
        int encodedLength2 = writeSbeEncodedRequestBuffer(buffer2, 0, new byte[]{12, 123, 13});

        // and the first buffer has already been read
        reader.wrap(buffer1, 0, encodedLength1);
        reader.getResource();

        // when we read the second buffer
        reader.wrap(buffer2, 0, encodedLength2);

        // then the resource is returned correctly
        DirectBuffer resourceResultBuffer = reader.getResource();

        assertThat(resourceResultBuffer.capacity()).isEqualTo(3);

        byte[] resource = new byte[3];
        resourceResultBuffer.getBytes(0, resource);
        assertThat(resource).containsExactly((byte) 12, (byte) 123, (byte) 13);
    }
}
