package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.tngp.broker.test.util.BufferAssert.assertThatBuffer;

import java.nio.charset.StandardCharsets;

import org.camunda.tngp.client.impl.cmd.taskqueue.CompleteTaskRequestWriter;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.MockitoAnnotations;

import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class CompleteTaskInstanceRequestWriterTest
{

    protected static final byte[] PAYLOAD = "bar".getBytes(StandardCharsets.UTF_8);

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Before
    public void setUp()
    {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void shouldSetProperties()
    {
        // given
        final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();
        requestWriter.taskId(123L)
            .shardId(876)
            .resourceId(456)
            .payload(PAYLOAD, 0, PAYLOAD.length);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

        // when
        requestWriter.write(buffer, 10);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 10);

        final CompleteTaskDecoder bodyDecoder = new CompleteTaskDecoder();
        bodyDecoder.wrap(buffer, 10 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(headerDecoder.shardId()).isEqualTo(876);
        assertThat(headerDecoder.resourceId()).isEqualTo(456);
        assertThat(headerDecoder.blockLength()).isEqualTo(CompleteTaskDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.schemaId()).isEqualTo(CompleteTaskDecoder.SCHEMA_ID);
        assertThat(headerDecoder.templateId()).isEqualTo(CompleteTaskDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.version()).isEqualTo(CompleteTaskDecoder.SCHEMA_VERSION);

        assertThat(bodyDecoder.consumerId()).isEqualTo(0);
        assertThat(bodyDecoder.taskId()).isEqualTo(123L);

        final UnsafeBuffer payloadBuffer = new UnsafeBuffer(new byte[bodyDecoder.payloadLength()]);
        bodyDecoder.getPayload(payloadBuffer, 0, bodyDecoder.payloadLength());
        assertThatBuffer(payloadBuffer).hasBytes(PAYLOAD);
    }

    @Test
    public void shouldReturnWriteLength()
    {
        // given
        final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();
        requestWriter.taskId(123L)
            .shardId(876)
            .resourceId(456)
            .payload(PAYLOAD, 0, PAYLOAD.length);

        // when
        final int length = requestWriter.getLength();

        // then
        assertThat(length).isEqualTo(MessageHeaderDecoder.ENCODED_LENGTH +
                CompleteTaskDecoder.BLOCK_LENGTH +
                CompleteTaskDecoder.payloadHeaderLength() +
                PAYLOAD.length);
    }

    @Test
    public void shouldValidateMissingTaskId()
    {
        // given
        final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();
        requestWriter
            .shardId(876)
            .resourceId(456)
            .payload(PAYLOAD, 0, PAYLOAD.length);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("No task id set");

        // when
        requestWriter.validate();
    }

    @Test
    public void shouldValidateMissingTaskResourceId()
    {
        // given
        final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();
        requestWriter
            .shardId(0)
            .taskId(0)
            .payload(PAYLOAD, 0, PAYLOAD.length);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("No task queue id set");

        // when
        requestWriter.validate();
    }


    @Test
    public void shouldResetAfterWrite()
    {
        // given
        final CompleteTaskRequestWriter requestWriter = new CompleteTaskRequestWriter();
        requestWriter
            .taskId(123L)
            .shardId(876)
            .resourceId(456)
            .payload(PAYLOAD, 0, PAYLOAD.length);

        final UnsafeBuffer buffer = new UnsafeBuffer(new byte[1024]);

        requestWriter.write(buffer, 0);

        // when
        requestWriter.write(buffer, 0);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        headerDecoder.wrap(buffer, 0);

        assertThat(headerDecoder.shardId()).isEqualTo(MessageHeaderDecoder.shardIdNullValue());
        assertThat(headerDecoder.resourceId()).isEqualTo(MessageHeaderDecoder.resourceIdNullValue());

        final CompleteTaskDecoder bodyDecoder = new CompleteTaskDecoder();
        bodyDecoder.wrap(buffer, headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.consumerId()).isEqualTo(0);
        assertThat(bodyDecoder.taskId()).isEqualTo(CompleteTaskDecoder.taskIdNullValue());
        assertThat(bodyDecoder.payload()).isEqualTo("");
    }

}
