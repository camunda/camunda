package org.camunda.tngp.client.impl.cmd;

import java.nio.charset.Charset;

import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceEncoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.util.buffer.RequestWriter;

public class CreateTaskSubscriptionRequestWriter implements RequestWriter
{
    static final Charset CHARSET = Charset.forName(CreateTaskInstanceEncoder.taskTypeCharacterEncoding());

    protected MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected CreateTaskSubscriptionEncoder bodyEncoder = new CreateTaskSubscriptionEncoder();

    protected UnsafeBuffer taskTypeBuffer = new UnsafeBuffer(0, 0);

    protected short consumerId;
    protected int initialCredits;
    protected long lockDuration;

    @Override
    public int getLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
                CreateTaskSubscriptionEncoder.BLOCK_LENGTH +
                CreateTaskSubscriptionEncoder.taskTypeHeaderLength() +
                taskTypeBuffer.capacity();
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
            .initialCredits(initialCredits)
            .lockDuration(lockDuration)
            .putTaskType(taskTypeBuffer, 0, taskTypeBuffer.capacity());
    }

    public CreateTaskSubscriptionRequestWriter consumerId(short consumerId)
    {
        this.consumerId = consumerId;
        return this;
    }

    public CreateTaskSubscriptionRequestWriter initialCredits(int initialCredits)
    {
        this.initialCredits = initialCredits;
        return this;
    }

    public CreateTaskSubscriptionRequestWriter lockDuration(long lockDuration)
    {
        this.lockDuration = lockDuration;
        return this;
    }

    public CreateTaskSubscriptionRequestWriter taskType(String taskType)
    {
        final byte[] taskTypeBytes = taskType.getBytes(CHARSET);
        this.taskTypeBuffer.wrap(taskTypeBytes, 0, taskTypeBytes.length);
        return this;
    }

    public CreateTaskSubscriptionRequestWriter taskType(DirectBuffer buffer, int offset, int length)
    {
        this.taskTypeBuffer.wrap(buffer, offset, length);
        return this;
    }

    @Override
    public void validate()
    {
        // TODO implement and test

    }

}
