package org.camunda.tngp.taskqueue.protocol;

import static org.camunda.tngp.taskqueue.protocol.TaskInstanceState.NEW;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.taskqueue.TaskQueueContext;
import org.camunda.tngp.transport.protocol.MessageHeaderEncoder;
import org.camunda.tngp.transport.protocol.async.AsyncRequestHandler;
import org.camunda.tngp.transport.protocol.async.DeferredMessage;
import org.camunda.tngp.transport.protocol.async.DeferredMessagePool;

import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CreateTaskInstanceHandler implements AsyncRequestHandler
{
    protected final CreateTaskInstanceDecoder msgDecoder = new CreateTaskInstanceDecoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();
    protected final ClaimedFragment claimedLogFragment = new ClaimedFragment();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final AckEncoder ackEncoder = new AckEncoder();

    protected final Dispatcher logWriteBuffer;
    protected final DeferredMessagePool responsePool;

    public CreateTaskInstanceHandler(final TaskQueueContext ctx)
    {
        logWriteBuffer = ctx.getLog().getWriteBuffer();
        responsePool = ctx.getResponsePool();
    }

    public int getTemplateId()
    {
        return CreateTaskInstanceDecoder.TEMPLATE_ID;
    }

    public long onRequest(
        final long requestId,
        final int channelId,
        final DirectBuffer msg,
        final int offset,
        final int length,
        final int sbeBlockLength,
        final int sbeSchemaVersion)
    {
        final int responseLength = ackEncoder.sbeBlockLength() + messageHeaderEncoder.encodedLength();
        final DeferredMessage response = responsePool.takeNext();

        long claimedLogPos = -1;

        try
        {
            if(response != null)
            {
                claimedLogPos = claimLogFragment(length, sbeBlockLength);
                // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
                claimedLogPos -= claimedLogFragment.getFragmentLength();

                if(claimedLogPos >= 0)
                {
                    final long taskInstanceId = claimedLogPos; // TODO: use id generator

                    writeTaskInstance(msg, offset, sbeBlockLength, sbeSchemaVersion, taskInstanceId);

                    if(response.allocate(channelId, responseLength))
                    {
                        writeAck(response, requestId, taskInstanceId);
                        response.defer(this, claimedLogPos);
                        claimedLogFragment.commit();
                    }
                }
            }
        }
        finally
        {
            if(claimedLogFragment.isOpen())
            {
                // claimedLogFragment.abort();
                claimedLogPos = -1;
            }
            if(response != null && !response.isDeferred())
            {
                response.abort();
            }
        }

        return claimedLogPos;
    }

    @Override
    public void onAsyncWorkCompleted(
            final DeferredMessage deferredMessage,
            final DirectBuffer asyncWorkBuffer,
            final int offset,
            final int length)
    {
        deferredMessage.commit();
    }

    private void writeTaskInstance(
            final DirectBuffer msg,
            final int offset,
            final int sbeBlockLength,
            final int sbeSchemaVersion,
            long taskInstanceId)
    {
        // encode header
        messageHeaderEncoder.wrap(claimedLogFragment.getBuffer(), claimedLogFragment.getOffset());
        messageHeaderEncoder
            .blockLength(taskInstanceEncoder.sbeBlockLength())
            .templateId(taskInstanceEncoder.sbeTemplateId())
            .schemaId(taskInstanceEncoder.sbeSchemaId())
            .version(taskInstanceEncoder.sbeSchemaVersion());

        // encode task instance
        int taskInstanceOffset = claimedLogFragment.getOffset() + messageHeaderEncoder.encodedLength();
        taskInstanceEncoder.wrap(claimedLogFragment.getBuffer(), taskInstanceOffset);
        taskInstanceEncoder
            .id(taskInstanceId)
            .version(1)
            .state(NEW);

        msgDecoder.wrap(msg, offset, sbeBlockLength, sbeSchemaVersion);

        final int taskTypeOffset = msgDecoder.limit() + CreateTaskInstanceDecoder.taskTypeHeaderLength();
        final int taskTypeLength = msgDecoder.taskTypeLength();
        taskInstanceEncoder.putTaskType(msg, taskTypeOffset, taskTypeLength);

        msgDecoder.limit(taskTypeOffset + taskTypeLength);
        final int payloadOffset = msgDecoder.limit() + CreateTaskInstanceDecoder.payloadHeaderLength();
        final int payloadLength = msgDecoder.payloadLength();
        taskInstanceEncoder.putPayload(msg, payloadOffset, payloadLength);
    }

    private void writeAck(
            final DeferredMessage response,
            final long requestId,
            final long taskInstanceId)
    {

        final ClaimedFragment claimedFragment = response.getClaimedFragment();
        final MutableDirectBuffer buffer = claimedFragment.getBuffer();

        messageHeaderEncoder.wrap(buffer, claimedFragment.getOffset());
        messageHeaderEncoder
            .blockLength(ackEncoder.sbeBlockLength())
            .templateId(taskInstanceEncoder.sbeTemplateId())
            .schemaId(taskInstanceEncoder.sbeSchemaId())
            .version(taskInstanceEncoder.sbeSchemaVersion());

        ackEncoder.wrap(buffer, claimedFragment.getOffset() + messageHeaderEncoder.encodedLength());
        ackEncoder
            .requestId(requestId)
            .taskId(taskInstanceId);
    }

    private long claimLogFragment(final int length, final int sbeBlockLength)
    {
        long claimedPos;

        final int encodedTaskInstanceLength =
                (length - sbeBlockLength) // length of payload and task type
                + taskInstanceEncoder.sbeBlockLength()
                + messageHeaderEncoder.encodedLength();

        do
        {
            claimedPos = logWriteBuffer.claim(claimedLogFragment, encodedTaskInstanceLength);
        }
        while(claimedPos == -2);

        return claimedPos;
    }
}
