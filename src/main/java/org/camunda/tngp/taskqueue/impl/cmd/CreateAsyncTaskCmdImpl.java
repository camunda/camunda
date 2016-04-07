package org.camunda.tngp.taskqueue.impl.cmd;

import org.camunda.tngp.taskqueue.client.cmd.CreateAsyncTaskCmd;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;
import org.camunda.tngp.taskqueue.protocol.AckDecoder;
import org.camunda.tngp.taskqueue.protocol.CreateTaskInstanceEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CreateAsyncTaskCmdImpl extends AbstractSetPayloadCmd<Long, CreateAsyncTaskCmd> implements CreateAsyncTaskCmd
{

    protected final CreateTaskInstanceEncoder requestEncoder = new CreateTaskInstanceEncoder();
    protected final AckDecoder ackDecoder = new AckDecoder();

    protected byte[] taskType = null;
    protected int taskTypeLength = 0;

    public CreateAsyncTaskCmdImpl(TngpClientImpl tngpClient)
    {
        super(tngpClient, AckDecoder.SCHEMA_ID, AckDecoder.TEMPLATE_ID);
    }

    @Override
    public int getRequestLength()
    {
        return headerEncoder.encodedLength()
             + requestEncoder.sbeBlockLength()
             + CreateTaskInstanceEncoder.taskTypeHeaderLength()
             + taskTypeLength
             + CreateTaskInstanceEncoder.payloadHeaderLength()
             + payloadLength;
    }

    @Override
    public void writeRequest(MutableDirectBuffer buffer, int offset)
    {
        int writeOffset = offset;

        headerEncoder.wrap(buffer, writeOffset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, writeOffset)
            .putTaskType(taskType, 0, taskTypeLength)
            .putPayload(payload, 0, payloadLength);
    }

    @Override
    protected Long readReponseBody(DirectBuffer responseBuffer, int offset, int actingBlockLength, int actingVersion)
    {
        ackDecoder.wrap(responseBuffer, offset, actingBlockLength, actingVersion);

        return ackDecoder.taskId();
    }

    @Override
    public CreateAsyncTaskCmd taskType(String taskType)
    {
        this.taskType = taskType.getBytes(CHARSET);
        this.taskTypeLength = this.taskType.length;
        return this;
    }

    @Override
    public void reset()
    {
        super.reset();

        taskType = null;
        taskTypeLength = 0;
    }
}
