package org.camunda.tngp.broker.taskqueue.handler;

import static org.camunda.tngp.taskqueue.data.TaskInstanceState.NEW;

import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.protocol.taskqueue.AckEncoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskInstanceDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CreateTaskInstanceHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    final static int ACK_LENGTH = AckEncoder.BLOCK_LENGTH + MessageHeaderEncoder.ENCODED_LENGTH;

    protected final byte[] taskTypeReadBuffer = new byte[256];

    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final CreateTaskInstanceDecoder requestDecoder = new CreateTaskInstanceDecoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();
    protected final ClaimedFragment claimedLogFragment = new ClaimedFragment();
    protected final MessageHeaderEncoder messageHeaderEncoder = new MessageHeaderEncoder();
    protected final AckEncoder ackEncoder = new AckEncoder();

    public long onRequest(
        final TaskQueueContext ctx,
        final DirectBuffer msg,
        final int offset,
        final int length,
        final DeferredResponse response)
    {
        final IdGenerator taskInstanceIdGenerator = ctx.getTaskInstanceIdGenerator();
        final Log log = ctx.getLog();

        headerDecoder.wrap(msg, offset);
        final int bodyOffset = offset + headerDecoder.encodedLength();

        requestDecoder.wrap(msg, bodyOffset, headerDecoder.blockLength(), headerDecoder.version());
        long claimedLogPos = -1;

        try
        {
            if(response.allocate(ACK_LENGTH))
            {
                claimedLogPos = claimLogFragment(log, length - headerDecoder.encodedLength(), headerDecoder.blockLength());
                // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
                claimedLogPos -= BitUtil.align(claimedLogFragment.getFragmentLength(), 8);

                if (claimedLogPos >= 0)
                {
                    final long taskInstanceId = taskInstanceIdGenerator.nextId();

                    writeTaskInstance(msg, bodyOffset, headerDecoder.blockLength(), headerDecoder.version(), ctx, taskInstanceId);

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
            final TaskQueueContext ctx,
            final long taskInstanceId)
    {
        final MutableDirectBuffer writeBuffer = claimedLogFragment.getBuffer();

        int writeOffset = claimedLogFragment.getOffset();

        messageHeaderEncoder.wrap(writeBuffer, writeOffset);
        messageHeaderEncoder
            .blockLength(taskInstanceEncoder.sbeBlockLength())
            .templateId(taskInstanceEncoder.sbeTemplateId())
            .schemaId(taskInstanceEncoder.sbeSchemaId())
            .version(taskInstanceEncoder.sbeSchemaVersion())
            .resourceId(ctx.getResourceId());

        writeOffset += messageHeaderEncoder.encodedLength();

        final int taskTypeLength = requestDecoder.taskTypeLength();
        requestDecoder.getTaskType(taskTypeReadBuffer, 0, taskTypeLength);
        final int taskTypeHashCode = TaskTypeHash.hashCode(taskTypeReadBuffer, taskTypeLength);

        taskInstanceEncoder.wrap(writeBuffer, writeOffset);
        taskInstanceEncoder
            .id(taskInstanceId)
            .version(1)
            .state(NEW)
            .taskTypeHash(taskTypeHashCode)
            .putTaskType(taskTypeReadBuffer, 0, taskTypeLength);

        requestDecoder.wrap(msg, readOffset, sbeBlockLength, sbeSchemaVersion);

        readOffset += sbeBlockLength + CreateTaskInstanceDecoder.taskTypeHeaderLength() + taskTypeLength;

        requestDecoder.limit(readOffset);

        readOffset += CreateTaskInstanceDecoder.payloadHeaderLength();

        taskInstanceEncoder.putPayload(msg, readOffset, requestDecoder.payloadLength());
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

    private long claimLogFragment(Log log, final int length, final int sbeBlockLength)
    {
        long claimedPos;

        final int encodedTaskInstanceLength =
                (length - sbeBlockLength) // length of payload and task type (including headers)
                + taskInstanceEncoder.sbeBlockLength()
                + messageHeaderEncoder.encodedLength();

        do
        {
            claimedPos = log.getWriteBuffer().claim(claimedLogFragment, encodedTaskInstanceLength, log.getId());
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
            final Object attachement,
            final long logPosition)
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
