package org.camunda.tngp.client.impl.cmd;

import java.nio.charset.Charset;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.taskqueue.CloseTaskSubscriptionEncoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;

public class CloseTaskSubscriptionRequestWriter implements RequestWriter
{
    static final Charset CHARSET = Charset.forName(CreateTaskInstanceEncoder.taskTypeCharacterEncoding());

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected CloseTaskSubscriptionEncoder bodyEncoder = new CloseTaskSubscriptionEncoder();

    protected short consumerId;
    protected long subscriptionId;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                CreateTaskSubscriptionEncoder.BLOCK_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        offset += headerEncoder.encodedLength();

        bodyEncoder.wrap(buffer, offset)
            .consumerId(consumerId)
            .subscriptionId(subscriptionId);
    }

    public CloseTaskSubscriptionRequestWriter consumerId(short consumerId)
    {
        this.consumerId = consumerId;
        return this;
    }

    public CloseTaskSubscriptionRequestWriter subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    @Override
    public void validate()
    {
        // TODO implement and test

    }

}
