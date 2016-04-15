package org.camunda.tngp.broker.taskqueue.handler;

import java.nio.channels.FileChannel;

import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.log.index.LogIndex;
import org.camunda.tngp.protocol.taskqueue.AckEncoder;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.NackEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class CompleteTaskHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    protected final CompleteTaskDecoder requestDecoder = new CompleteTaskDecoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();
    protected final TaskInstanceDecoder taskInstanceDecoder = new TaskInstanceDecoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final AckEncoder ackEncoder = new AckEncoder();
    protected final NackEncoder nackEncoder = new NackEncoder();

    protected final ClaimedFragment claimedFragment = new ClaimedFragment();
    protected final TaskInstanceLogFragementHandler logReader = new TaskInstanceLogFragementHandler();

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response,
            final int sbeBlockLength,
            final int sbeSchemaVersion)
    {
        final HashIndex taskInstanceIndex = ctx.getLockedTaskInstanceIndex();
        final Log log = ctx.getLog();

        final TaskInstanceReader taskInstanceReader = logReader.taskInstanceReader;
        final TaskInstanceDecoder taskInstanceDecoder = taskInstanceReader.getDecoder();

        requestDecoder.wrap(msg, offset, sbeBlockLength, sbeSchemaVersion);

        final long taskId = requestDecoder.taskId();
        final long clientId = 0; // TODO
        final int payloadLength = requestDecoder.payloadLength();
        final int payloadOffset = requestDecoder.limit() + CompleteTaskDecoder.payloadHeaderLength();

        if (response.allocate(ackResponseLength()))
        {
            int errorCode = -1;

            final long lastTaskPosition = taskInstanceIndex.getLong(taskId, -1);
            if (lastTaskPosition != -1)
            {
                taskInstanceReader.reset();
                log.pollFragment(lastTaskPosition, logReader);

                final TaskInstanceState state = taskInstanceDecoder.state();
                final long lockOwnerId = taskInstanceDecoder.lockOwnerId();

                if (state == TaskInstanceState.LOCKED && lockOwnerId == clientId)
                {
                    final long claimedPosition = claimLogFragment(log, payloadLength, taskInstanceReader);

                    if (claimedPosition >= 0)
                    {
                        writeCompletedTaskInstanceLogEntry(
                                ctx,
                                claimedFragment,
                                taskInstanceReader,
                                msg,
                                payloadOffset,
                                payloadLength);

                        writeAck(ctx, response, taskId);
                        response.defer(claimedPosition, this, ctx);
                    }
                    else
                    {
                        // NACK: cannot complete, backpressured by log write buffer
                        errorCode = 1;
                    }
                }
                else
                {
                    // NACK: cannot complete, illegal state
                    errorCode = 1;
                }
            }
            else
            {
                // NACK: task not found
                errorCode = 1;
            }

            if(errorCode > 0)
            {
                writeNack(response, taskId);
                response.commit();
            }
        }
        else
        {
            // TODO: cannot allocate response in response buffer
            // backpressure channel
        }

        return 0;
    }

    protected void writeAck(TaskQueueContext ctx, DeferredResponse response, long taskInstanceId)
    {
        final MutableDirectBuffer buffer = response.getBuffer();

        int writeOffset = response.getClaimedOffset();

        headerEncoder.wrap(buffer, writeOffset);
        headerEncoder
            .blockLength(ackEncoder.sbeBlockLength())
            .templateId(ackEncoder.sbeTemplateId())
            .schemaId(ackEncoder.sbeSchemaId())
            .version(ackEncoder.sbeSchemaVersion())
            .resourceId(ctx.getResourceId());

        writeOffset += headerEncoder.encodedLength();

        ackEncoder.wrap(buffer, writeOffset);
        ackEncoder.taskId(taskInstanceId);
    }

    protected void writeNack(DeferredResponse response, long taskInstanceId)
    {
        final MutableDirectBuffer buffer = response.getBuffer();

        int writeOffset = response.getClaimedOffset();

        headerEncoder.wrap(buffer, writeOffset);
        headerEncoder
            .blockLength(nackEncoder.sbeBlockLength())
            .templateId(nackEncoder.sbeTemplateId())
            .schemaId(nackEncoder.sbeSchemaId())
            .version(nackEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        nackEncoder.wrap(buffer, writeOffset);
        // TODO: provide error code
    }

    protected long claimLogFragment(Log log, final int payloadLength, final TaskInstanceReader taskInstanceReader)
    {
        final int lengthMinusPayload = taskInstanceReader.getLength() - taskInstanceReader.getPayloadLength();
        final int taskInstanceLength = lengthMinusPayload + TaskInstanceEncoder.payloadHeaderLength() + payloadLength;

        long claimedPosition = -1;
        do
        {
            claimedPosition = log.getWriteBuffer().claim(claimedFragment, taskInstanceLength);
        }
        while(claimedPosition == -2);

        // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
        claimedPosition -= BitUtil.align(claimedFragment.getFragmentLength(), 8);

        return claimedPosition;
    }

    protected void writeCompletedTaskInstanceLogEntry(
            final TaskQueueContext ctx,
            final ClaimedFragment claimedLogFragment,
            final TaskInstanceReader reader,
            final DirectBuffer payloadBuffer,
            final int payloadOffset,
            final int payloadLength)
    {
        final MutableDirectBuffer writeBuffer = claimedLogFragment.getBuffer();
        final TaskInstanceDecoder decoder = reader.getDecoder();

        int writeOffset = claimedLogFragment.getOffset();

        headerEncoder.wrap(writeBuffer, writeOffset)
            .blockLength(taskInstanceEncoder.sbeBlockLength())
            .templateId(taskInstanceEncoder.sbeTemplateId())
            .schemaId(taskInstanceEncoder.sbeSchemaId())
            .version(taskInstanceEncoder.sbeSchemaVersion())
            .resourceId(ctx.getResourceId());

        writeOffset += headerEncoder.encodedLength();

        taskInstanceEncoder.wrap(writeBuffer, writeOffset);
        taskInstanceEncoder
            .id(decoder.id())
            .version(decoder.version() + 1)
            .state(TaskInstanceState.COMPLETED)
            .taskTypeHash(decoder.taskTypeHash());

        taskInstanceEncoder.putTaskType(reader.getReadBuffer(), reader.getTaskTypeOffset(), reader.getTaskTypeLength());
        taskInstanceEncoder.putPayload(payloadBuffer, payloadOffset, payloadLength);

        claimedLogFragment.commit();
    }

    private static int ackResponseLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH + AckEncoder.BLOCK_LENGTH;
    }

    @Override
    public void onAsyncWorkCompleted(
            final DeferredResponse response,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final Object attachment,
            final long logPosition)
    {
        response.commit();

        // remove completed task from index
        final int readOffset = offset + MessageHeaderEncoder.ENCODED_LENGTH;
        final long taskId = taskInstanceDecoder.wrap(buffer, readOffset, taskInstanceDecoder.sbeBlockLength(), taskInstanceDecoder.sbeSchemaVersion()).id();
        ((TaskQueueContext)attachment).getLockedTaskInstanceIndex().remove(taskId);
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

    class TaskInstanceLogFragementHandler implements LogFragmentHandler
    {
        protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();

        @Override
        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            taskInstanceReader.read(fileChannel, offset, length);
        }

        public void reset()
        {
            taskInstanceReader.reset();
        }

    }

}
