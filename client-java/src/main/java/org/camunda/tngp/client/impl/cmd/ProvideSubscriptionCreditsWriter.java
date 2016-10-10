package org.camunda.tngp.client.impl.cmd;

import org.agrona.MutableDirectBuffer;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;

public class ProvideSubscriptionCreditsWriter implements RequestWriter
{

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected ProvideSubscriptionCreditsEncoder bodyEncoder = new ProvideSubscriptionCreditsEncoder();

    protected short consumerId;
    protected long subscriptionId;
    protected int credits;

    public ProvideSubscriptionCreditsWriter consumerId(short consumerId)
    {
        this.consumerId = consumerId;
        return this;
    }

    public ProvideSubscriptionCreditsWriter subscriptionId(long subscriptionId)
    {
        this.subscriptionId = subscriptionId;
        return this;
    }

    public ProvideSubscriptionCreditsWriter credits(int credits)
    {
        this.credits = credits;
        return this;
    }

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + ProvideSubscriptionCreditsEncoder.BLOCK_LENGTH;
    }

    @Override
    public void write(MutableDirectBuffer buffer, int offset)
    {
        headerEncoder.wrap(buffer, offset)
            .blockLength(bodyEncoder.sbeBlockLength())
            .schemaId(bodyEncoder.sbeSchemaId())
            .templateId(bodyEncoder.sbeTemplateId())
            .version(bodyEncoder.sbeSchemaVersion());

        bodyEncoder.wrap(buffer, offset + headerEncoder.encodedLength())
            .consumerId(consumerId)
            .subscriptionId(subscriptionId)
            .credits(credits);
    }

    @Override
    public void validate()
    {
    }



}
