package org.camunda.tngp.broker.taskqueue.handler;

import java.nio.channels.FileChannel;

import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.hashindex.Bytes2LongHashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderEncoder;
import org.camunda.tngp.protocol.taskqueue.PollAndLockTasksDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceDecoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceEncoder;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class LockTaskBatchHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    private static final int TASK_TYPE_MAXLENGTH = 256;

    protected final PollAndLockTasksDecoder requestDecoder = new PollAndLockTasksDecoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final LockedTaskBatchEncoder responseEncoder = new LockedTaskBatchEncoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();
    protected final TaskInstanceDecoder taskInstanceDecoder = new TaskInstanceDecoder();

    protected final byte[] taskTypeBuff = new byte[TASK_TYPE_MAXLENGTH];

    protected final TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected final ClaimedFragment claimedLogFragment = new ClaimedFragment();
    protected final LockableTaskFinder lockableTaskFinder = new LockableTaskFinder();

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final Bytes2LongHashIndex taskTypePositionIndex = ctx.getTaskTypePositionIndex().getIndex();
        final Log log = ctx.getLog();

        headerDecoder.wrap(buffer, offset);

        requestDecoder.wrap(buffer, offset + headerDecoder.encodedLength(), headerDecoder.blockLength(), headerDecoder.version());

        final long consumerId = requestDecoder.consumerId();
        final long lockTime = requestDecoder.lockTime();
//        final long maxTask = decoder.maxTasks();

        final int taskTypeLength = requestDecoder.taskTypeLength();
        requestDecoder.getTaskType(taskTypeBuff, 0, TASK_TYPE_MAXLENGTH);
        int taskTypeHash = TaskTypeHash.hashCode(taskTypeBuff, taskTypeLength);

        final long now = System.currentTimeMillis();
        final long lockTimeout = now + lockTime;

        // scan the log for lockable tasks
        lockableTaskFinder.init(taskTypeHash, taskTypeBuff, taskTypeLength);
        long scanPos = Math.max(taskTypePositionIndex.get(taskTypeBuff, -1), log.getInitialPosition());

        long lockableTaskPosition = -1;
        do
        {
            scanPos = log.pollFragment(scanPos, lockableTaskFinder);
            lockableTaskPosition = lockableTaskFinder.lockableTaskPosition;
        }
        while(scanPos > 0 && lockableTaskPosition == -1);

        if(lockableTaskPosition != -1)
        {
            try
            {
                lockTask(ctx, response, consumerId, lockTimeout, taskTypeLength);
            }
            finally
            {
                if(claimedLogFragment.isOpen())
                {
                    claimedLogFragment.abort();
                }
                if(!response.isDeferred())
                {
                    response.abort();
                }
            }
        }
        else
        {
            if(response.allocate(emptyResponseLength()))
            {
                writeCommonResponse(ctx, response, consumerId, lockTimeout);
                responseEncoder.tasksCount(0);
                response.commit();
            }
            else
            {
                System.err.println("Could not allocate response");
            }
        }

        return 0;
    }

    protected void lockTask(
            final TaskQueueContext ctx,
            final DeferredResponse response,
            final long consumerId,
            final long lockTimeout,
            final int taskTypeLength)
    {
        final Log log = ctx.getLog();
        final TaskInstanceReader taskInstanceReader = lockableTaskFinder.reader;
        final int payloadLength = taskInstanceReader.getPayloadLength();
        taskInstanceReader.readPayload();

        long claimedLogPosition = claimLogFragment(log, taskInstanceReader);
        // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
        claimedLogPosition -= BitUtil.align(claimedLogFragment.getFragmentLength(), 8);

        if (claimedLogPosition >= 0)
        {
            writeLockedTaskInstanceLogEntry(ctx, claimedLogFragment, taskInstanceReader, consumerId, lockTimeout);

            if (response.allocate(lockTaskBatchResponseLength(taskTypeLength, payloadLength)))
            {
                writeCommonResponse(ctx, response, consumerId, lockTimeout);

                responseEncoder.tasksCount(1)
                    .next()
                        .taskId(taskInstanceReader.getDecoder().id())
                        .putPayload(taskInstanceReader.getPayloadReadBuffer(), 0, taskInstanceReader.getPayloadLength());

                response.defer(claimedLogPosition, this, null);
                claimedLogFragment.commit();
            }
        }
    }

    protected void writeCommonResponse(
            final TaskQueueContext ctx,
            final DeferredResponse response,
            final long consumerId,
            final long lockTime)
    {
        final MutableDirectBuffer responseBuffer = response.getBuffer();

        int writeOffset = response.getClaimedOffset();

        headerEncoder.wrap(responseBuffer, writeOffset)
            .blockLength(responseEncoder.sbeBlockLength())
            .templateId(responseEncoder.sbeTemplateId())
            .schemaId(responseEncoder.sbeSchemaId())
            .version(responseEncoder.sbeSchemaVersion())
            .resourceId(ctx.getResourceId());

        writeOffset += headerEncoder.encodedLength();

        responseEncoder.wrap(responseBuffer, writeOffset)
            .lockTime(lockTime);
    }

    protected long claimLogFragment(final Log log, final TaskInstanceReader taskInstanceReader)
    {
        long claimedPosition = -1;

        do
        {
            claimedPosition = log.getWriteBuffer().claim(claimedLogFragment, taskInstanceReader.getLength());
        }
        while(claimedPosition == -2);

        return claimedPosition;
    }

    private int lockTaskBatchResponseLength(int taskTypeLength, int payloadLength)
    {
        return emptyResponseLength() +
               TasksEncoder.sbeBlockLength() +
               TasksEncoder.payloadHeaderLength() +
               payloadLength;
    }

    private static int emptyResponseLength()
    {
        return MessageHeaderEncoder.ENCODED_LENGTH +
               LockedTaskBatchEncoder.BLOCK_LENGTH +
               TasksEncoder.sbeHeaderSize();
    }

    private void writeLockedTaskInstanceLogEntry(
            TaskQueueContext ctx,
            ClaimedFragment claimedLogFragment,
            TaskInstanceReader reader,
            long consumerId,
            long lockTimeout)
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
            .prevVersionPosition(reader.getLogPosition())
            .state(TaskInstanceState.LOCKED)
            .lockOwnerId(consumerId)
            .lockTime(lockTimeout)
            .taskTypeHash(decoder.taskTypeHash());

        taskInstanceEncoder.putTaskType(reader.getBlockBuffer(), reader.getTaskTypeOffset(), reader.getTaskTypeLength());
        taskInstanceEncoder.putPayload(reader.getPayloadReadBuffer(), 0, reader.getPayloadLength());
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

    static class LockableTaskFinder implements LogFragmentHandler
    {
        final TaskInstanceReader reader;

        int taskTypeHashToPoll;
        byte[] taskTypeToPoll;
        int taskTypeToPollLength;
        byte[] taskType = new byte[TASK_TYPE_MAXLENGTH];

        long lockableTaskPosition;

        public LockableTaskFinder()
        {
            reader = new TaskInstanceReader();
        }

        void init(
                int taskTypeHashToPoll,
                byte[] taskTypeToPoll,
                int taskTypeToPollLength)
        {
            this.taskTypeHashToPoll = taskTypeHashToPoll;
            this.taskTypeToPoll = taskTypeToPoll;
            this.taskTypeToPollLength = taskTypeToPollLength;
            this.lockableTaskPosition = -1;
        }

        @Override
        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            if(reader.readBlock(position, fileChannel, offset, length))
            {
                final TaskInstanceDecoder decoder = reader.getDecoder();

                final TaskInstanceState state = decoder.state();
                final long taskTypeHash = decoder.taskTypeHash();

                if (taskTypeHash == taskTypeHashToPoll && state == TaskInstanceState.NEW)
                {
                    if (taskTypeEqual(reader, taskTypeToPoll, taskTypeToPollLength))
                    {
                        lockableTaskPosition = position;
                    }
                }
            }
            else
            {
                System.err.println("could not task instance at log position "+position);
            }
        }

        private static boolean taskTypeEqual(
                TaskInstanceReader reader,
                byte[] taskTypeToPoll,
                int taskTypeToPollLength)
        {

            final DirectBuffer readBuffer = reader.getBlockBuffer();
            final int taskTypeOffset = reader.getTaskTypeOffset();
            final int taskTypeLength = reader.getTaskTypeLength();

            if(taskTypeToPollLength == taskTypeLength)
            {
                boolean taskTypeEqual = true;

                for (int i = 0; i < taskTypeToPollLength && taskTypeEqual; i++)
                {
                    taskTypeEqual &= taskTypeToPoll[i] == readBuffer.getByte(taskTypeOffset + i);
                }

                return taskTypeEqual;
            }
            else
            {
                return false;
            }
        }

    }
}
