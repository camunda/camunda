package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.tngp.client.impl.cmd.wf.start.StartWorkflowInstanceRequestWriter;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.runtime.MessageHeaderEncoder;
import org.camunda.tngp.protocol.wf.runtime.StartWorkflowInstanceDecoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class StartWorkflowInstanceRequestWriterTest
{
    protected static final byte[] WF_TYPE_KEY = new byte[] {1, 1, 2, 3, 5, 8};

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testWritingWithId()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionId(12345L);

        // when
        writer.write(buffer, 42);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final StartWorkflowInstanceDecoder bodyDecoder = new StartWorkflowInstanceDecoder();

        headerDecoder.wrap(buffer, 42);

        assertThat(headerDecoder.blockLength()).isEqualTo(StartWorkflowInstanceDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(StartWorkflowInstanceDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(StartWorkflowInstanceDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(StartWorkflowInstanceDecoder.SCHEMA_VERSION);
        assertThat(headerDecoder.resourceId()).isEqualTo(52);
        assertThat(headerDecoder.shardId()).isEqualTo(8686);

        bodyDecoder.wrap(buffer, 42 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        assertThat(bodyDecoder.wfDefinitionId()).isEqualTo(12345L);
    }

    @Test
    public void testGetEncodedLengthWithId()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionId(12345L);

        // when
        final int encodedLength = writer.getLength();

        // then
        assertThat(encodedLength).isEqualTo(
            MessageHeaderEncoder.ENCODED_LENGTH +
            StartWorkflowInstanceDecoder.BLOCK_LENGTH +
            StartWorkflowInstanceDecoder.wfDefinitionKeyHeaderLength()
        );
    }

    @Test
    public void testWritingWithKey()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionKey(WF_TYPE_KEY);

        // when
        writer.write(buffer, 42);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final StartWorkflowInstanceDecoder bodyDecoder = new StartWorkflowInstanceDecoder();

        headerDecoder.wrap(buffer, 42);

        assertThat(headerDecoder.blockLength()).isEqualTo(StartWorkflowInstanceDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(StartWorkflowInstanceDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(StartWorkflowInstanceDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(StartWorkflowInstanceDecoder.SCHEMA_VERSION);
        assertThat(headerDecoder.resourceId()).isEqualTo(52);
        assertThat(headerDecoder.shardId()).isEqualTo(8686);

        bodyDecoder.wrap(buffer, 42 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final byte[] returnedKey = new byte[WF_TYPE_KEY.length];
        bodyDecoder.getWfDefinitionKey(returnedKey, 0, WF_TYPE_KEY.length);

        assertThat(returnedKey).containsExactly(WF_TYPE_KEY);
    }

    @Test
    public void testGetEncodedLengthWithKey()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionKey(WF_TYPE_KEY);

        // when
        final int encodedLength = writer.getLength();

        // then
        assertThat(encodedLength).isEqualTo(
            MessageHeaderEncoder.ENCODED_LENGTH +
            StartWorkflowInstanceDecoder.BLOCK_LENGTH +
            StartWorkflowInstanceDecoder.wfDefinitionKeyHeaderLength() +
            WF_TYPE_KEY.length
        );
    }

    @Test
    public void testValidateAtLeastOneParameterSet()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Must set either workflow type id or key");

        // when
        writer.validate();
    }

    @Test
    public void testValidateNotMoreThanOneParameterSet()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionId(1234L)
            .wfDefinitionKey(WF_TYPE_KEY);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Must set either workflow type id or key");

        // when
        writer.validate();
    }

    @Test
    public void testValidateMessageKeyTooLong()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionKey(new byte[257]);

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("Key must not be longer than 256 bytes");

        // when
        writer.validate();
    }

    @Test
    public void testValidateMessageKeyLongEnough()
    {
        // given
        final StartWorkflowInstanceRequestWriter writer = new StartWorkflowInstanceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .wfDefinitionKey(new byte[256]);

        // when
        writer.validate();

        // then no exception is thrown
    }
}
