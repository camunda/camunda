package org.camunda.tngp.taskqueue.impl.cmd;

import org.camunda.tngp.taskqueue.client.cmd.CompleteTaskCmd;
import org.camunda.tngp.taskqueue.impl.TngpClientImpl;
import org.camunda.tngp.taskqueue.protocol.AckDecoder;
import org.camunda.tngp.taskqueue.protocol.CompleteTaskEncoder;
import org.camunda.tngp.taskqueue.protocol.MessageHeaderEncoder;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CompleteTaskCmdImpl extends AbstractSetPayloadCmd<Long, CompleteTaskCmd> implements CompleteTaskCmd
{
    protected final CompleteTaskEncoder requestEncoder = new CompleteTaskEncoder();
    protected final AckDecoder responseDecoder = new AckDecoder();

    protected long taskId;

    public CompleteTaskCmdImpl(TngpClientImpl client)
    {
        super(client, AckDecoder.SCHEMA_ID, AckDecoder.TEMPLATE_ID);
    }

    @Override
    public CompleteTaskCmd taskId(long taskId)
    {
        this.taskId = taskId;
        return this;
    }

    @Override
    protected int getRequestLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
               requestEncoder.sbeBlockLength() +
               CompleteTaskEncoder.payloadHeaderLength() +
               payloadLength;
    }

    @Override
    protected void writeRequest(MutableDirectBuffer buffer, int claimedOffset)
    {
        int writeOffset = claimedOffset;

        headerEncoder.wrap(buffer, writeOffset)
            .blockLength(requestEncoder.sbeBlockLength())
            .schemaId(requestEncoder.sbeSchemaId())
            .templateId(requestEncoder.sbeTemplateId())
            .version(requestEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        requestEncoder.wrap(buffer, writeOffset)
            .taskId(taskId)
            .putPayload(payload, 0, payloadLength);
    }

    @Override
    protected Long readReponseBody(
            DirectBuffer responseBuffer,
            int offset,
            int actingBlockLength,
            int actingVersion)
    {
        responseDecoder.wrap(responseBuffer, offset, actingBlockLength, actingVersion);

        return responseDecoder.taskId();
    }

    @Override
    public void reset()
    {
        super.reset();
        taskId = -1;
    }
}
