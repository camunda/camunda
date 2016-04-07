package org.camunda.tngp.taskqueue.protocol;

import static org.camunda.tngp.taskqueue.protocol.TaskInstanceState.NEW;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.worker.TaskQueueContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponsePool;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CreateTaskInstanceHandler implements SbeRequestHandler, ResponseCompletionHandler
{
    final static int ACK_LENGTH = AckEncoder.BLOCK_LENGTH + MessageHeaderEncoder.ENCODED_LENGTH;

    protected final CreateTaskInstanceDecoder msgDecoder = new CreateTaskInstanceDecoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();
    protected final ClaimedFragment claimedLogFragment = new ClaimedFragment();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final AckEncoder ackEncoder = new AckEncoder();

    protected final Dispatcher logWriteBuffer;
    protected final DeferredResponsePool responsePool;

    protected byte[] taskTypeReadBuffer = new byte[1024];

    protected IdGenerator taskInstanceIdGenerator;

    public CreateTaskInstanceHandler(final TaskQueueContext ctx)
    {
        logWriteBuffer = ctx.getLog().getWriteBuffer();
        responsePool = ctx.getResponsePool();
        taskInstanceIdGenerator = ctx.getTaskInstanceIdGenerator();
    }

    public int getTemplateId()
    {
        return CreateTaskInstanceDecoder.TEMPLATE_ID;
    }

    public long onRequest(
        final DirectBuffer msg,
        final int offset,
        final int length,
        final DeferredResponse response,
        final int sbeBlockLength,
        final int sbeSchemaVersion)
    {
        msgDecoder.wrap(msg, offset, sbeBlockLength, sbeSchemaVersion);
        long claimedLogPos = -1;

        try
        {
            if(response.allocate(ACK_LENGTH))
            {
                claimedLogPos = claimLogFragment(length, sbeBlockLength);
                // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
                claimedLogPos -= BitUtil.align(claimedLogFragment.getFragmentLength(), 8);

                if (claimedLogPos >= 0)
                {
                    final long taskInstanceId = taskInstanceIdGenerator.nextId();

                    writeTaskInstance(msg, offset, sbeBlockLength, sbeSchemaVersion, taskInstanceId);

                    writeAck(response, taskInstanceId);
                    response.defer(claimedLogPos, this, null);

                    claimedLogFragment.commit();
                }
            }
            else
            {
                System.err.println("Could no allocate response");
            }
        }
        finally
        {
            if(claimedLogFragment.isOpen())
            {
                claimedLogFragment.abort();
                claimedLogPos = -1;
            }
            if(!response.isDeferred())
            {
                response.abort();
            }
        }

        return claimedLogPos;
    }

    private void writeTaskInstance(
            final DirectBuffer msg,
            int readOffset,
            final int sbeBlockLength,
            final int sbeSchemaVersion,
            long taskInstanceId)
    {
        final MutableDirectBuffer writeBuffer = claimedLogFragment.getBuffer();

        int writeOffset = claimedLogFragment.getOffset();

        messageHeaderEncoder.wrap(writeBuffer, writeOffset);
        messageHeaderEncoder
            .blockLength(taskInstanceEncoder.sbeBlockLength())
            .templateId(taskInstanceEncoder.sbeTemplateId())
            .schemaId(taskInstanceEncoder.sbeSchemaId())
            .version(taskInstanceEncoder.sbeSchemaVersion());

        writeOffset += messageHeaderEncoder.encodedLength();

        final int taskTypeLength = msgDecoder.taskTypeLength();
        msgDecoder.getTaskType(taskTypeReadBuffer, 0, taskTypeLength);
        final int taskTypeHashCode = TaskTypeHash.hashCode(taskTypeReadBuffer, taskTypeLength);

        taskInstanceEncoder.wrap(writeBuffer, writeOffset);
        taskInstanceEncoder
            .id(taskInstanceId)
            .version(1)
            .state(NEW)
            .taskTypeHash(taskTypeHashCode)
            .putTaskType(taskTypeReadBuffer, 0, taskTypeLength);

        msgDecoder.wrap(msg, readOffset, sbeBlockLength, sbeSchemaVersion);

        readOffset += sbeBlockLength + CreateTaskInstanceDecoder.taskTypeHeaderLength() + taskTypeLength;

        msgDecoder.limit(readOffset);

        readOffset += CreateTaskInstanceDecoder.payloadHeaderLength();

        taskInstanceEncoder.putPayload(msg, readOffset, msgDecoder.payloadLength());
    }

    private void writeAck(
            final DeferredResponse response,
            final long taskInstanceId)
    {

        final MutableDirectBuffer buffer = response.getBuffer();

        int writeOffset = response.getClaimedOffset();

        messageHeaderEncoder.wrap(buffer, writeOffset);
        messageHeaderEncoder
            .blockLength(ackEncoder.sbeBlockLength())
            .templateId(ackEncoder.sbeTemplateId())
            .schemaId(ackEncoder.sbeSchemaId())
            .version(ackEncoder.sbeSchemaVersion());

        writeOffset += messageHeaderEncoder.encodedLength();

        ackEncoder.wrap(buffer, writeOffset);
        ackEncoder.taskId(taskInstanceId);
    }

    private long claimLogFragment(final int length, final int sbeBlockLength)
    {
        long claimedPos;

        final int encodedTaskInstanceLength =
                (length - sbeBlockLength) // length of payload and task type (including headers)
                + taskInstanceEncoder.sbeBlockLength()
                + messageHeaderEncoder.encodedLength();

        do
        {
            claimedPos = logWriteBuffer.claim(claimedLogFragment, encodedTaskInstanceLength);
        }
        while(claimedPos == -2);

        return claimedPos;
    }

    @Override
    public void onAsyncWorkCompleted(
            final DeferredResponse response,
            final DirectBuffer asyncWorkBuffer,
            final int offset,
            final int length,
            final Object attachement)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(
            final DeferredResponse response,
            final DirectBuffer asyncWorkBuffer,
            final int offset,
            final int length,
            final Object attachement)
    {
        response.abort();
    }
}
