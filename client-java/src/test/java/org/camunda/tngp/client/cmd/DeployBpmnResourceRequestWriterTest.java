package org.camunda.tngp.client.cmd;

import static org.assertj.core.api.Assertions.assertThat;

import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.client.impl.cmd.wf.deploy.DeployBpmnResourceRequestWriter;
import org.camunda.tngp.protocol.wf.MessageHeaderDecoder;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceDecoder;
import org.camunda.tngp.protocol.wf.DeployBpmnResourceEncoder;
import org.camunda.tngp.protocol.wf.MessageHeaderEncoder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class DeployBpmnResourceRequestWriterTest
{
    protected static final byte[] RESOURCE = new byte[] {1, 1, 2, 3, 5, 8};

    @Rule
    public ExpectedException exception = ExpectedException.none();

    @Test
    public void testWriting()
    {
        // given
        final DeployBpmnResourceRequestWriter writer = new DeployBpmnResourceRequestWriter();
        final MutableDirectBuffer buffer = new UnsafeBuffer(new byte[512]);

        writer
            .resourceId(52)
            .shardId(8686)
            .resource(RESOURCE);

        // when
        writer.write(buffer, 42);

        // then
        final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
        final DeployBpmnResourceDecoder bodyDecoder = new DeployBpmnResourceDecoder();

        headerDecoder.wrap(buffer, 42);

        assertThat(headerDecoder.blockLength()).isEqualTo(DeployBpmnResourceDecoder.BLOCK_LENGTH);
        assertThat(headerDecoder.templateId()).isEqualTo(DeployBpmnResourceDecoder.TEMPLATE_ID);
        assertThat(headerDecoder.schemaId()).isEqualTo(DeployBpmnResourceDecoder.SCHEMA_ID);
        assertThat(headerDecoder.version()).isEqualTo(DeployBpmnResourceDecoder.SCHEMA_VERSION);
        assertThat(headerDecoder.resourceId()).isEqualTo(52);
        assertThat(headerDecoder.shardId()).isEqualTo(8686);

        bodyDecoder.wrap(buffer, 42 + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final byte[] returnedResource = new byte[RESOURCE.length];
        bodyDecoder.getResource(returnedResource, 0, 6);

        assertThat(returnedResource).containsExactly(RESOURCE);
    }

    @Test
    public void testGetEncodedLength()
    {
        // given
        final DeployBpmnResourceRequestWriter writer = new DeployBpmnResourceRequestWriter();

        writer
            .resourceId(52)
            .shardId(8686)
            .resource(RESOURCE);

        // when
        final int encodedLength = writer.getLength();

        // then
        assertThat(encodedLength).isEqualTo(
            MessageHeaderEncoder.ENCODED_LENGTH +
            DeployBpmnResourceEncoder.resourceHeaderLength() +
            RESOURCE.length
        );
    }


    @Test
    public void testValidateWithoutResource()
    {
        // given
        final DeployBpmnResourceRequestWriter writer = new DeployBpmnResourceRequestWriter();

        // then
        exception.expect(RuntimeException.class);
        exception.expectMessage("No Bpmn Resource specified");

        // when
        writer.validate();
    }

}
