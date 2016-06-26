package org.camunda.tngp.client.impl.cmd.wf.deploy;

import org.camunda.tngp.client.impl.cmd.ClientRequestWriter;
import org.camunda.tngp.protocol.wf.repository.DeployBpmnResourceEncoder;
import org.camunda.tngp.protocol.wf.repository.MessageHeaderEncoder;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

public class DeployBpmnResourceRequestWriter implements ClientRequestWriter
{

    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final DeployBpmnResourceEncoder bodyEncoder = new DeployBpmnResourceEncoder();

    protected int resourceId;
    protected int shardId;

    protected final UnsafeBuffer resourceBuffer = new UnsafeBuffer(0, 0);

    public DeployBpmnResourceRequestWriter resource(byte[] resource)
    {
        resourceBuffer.wrap(resource);
        return this;
    }

    public DeployBpmnResourceRequestWriter resourceId(int resourceId)
    {
        this.resourceId = resourceId;
        return this;
    }

    public DeployBpmnResourceRequestWriter shardId(int shardId)
    {
        this.shardId = shardId;
        return this;
    }

    @Override
    public int getLength()
    {
        return headerEncoder.encodedLength() +
             DeployBpmnResourceEncoder.BLOCK_LENGTH +
             DeployBpmnResourceEncoder.resourceHeaderLength() +
             resourceBuffer.capacity();
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .templateId(bodyEncoder.sbeTemplateId())
            .schemaId(bodyEncoder.sbeSchemaId())
            .version(bodyEncoder.sbeSchemaVersion())
            .resourceId(resourceId)
            .shardId(shardId);

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .putResource(resourceBuffer, 0, resourceBuffer.capacity());

        resourceBuffer.wrap(0, 0);
    }

    @Override
    public void validate()
    {
        if (resourceBuffer.capacity() <= 0)
        {
            throw new RuntimeException("No Bpmn Resource specified");
        }
    }

}
