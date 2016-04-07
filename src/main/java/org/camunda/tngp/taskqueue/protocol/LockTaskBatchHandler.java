package org.camunda.tngp.taskqueue.protocol;

import java.nio.channels.FileChannel;

import org.camunda.tngp.dispatcher.ClaimedFragment;
import org.camunda.tngp.dispatcher.Dispatcher;
import org.camunda.tngp.hashindex.HashIndex;
import org.camunda.tngp.log.Log;
import org.camunda.tngp.log.LogFragmentHandler;
import org.camunda.tngp.taskqueue.protocol.LockedTaskBatchEncoder.TasksEncoder;
import org.camunda.tngp.taskqueue.worker.TaskQueueContext;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.BitUtil;
import uk.co.real_logic.agrona.DirectBuffer;
import uk.co.real_logic.agrona.MutableDirectBuffer;

public class LockTaskBatchHandler implements SbeRequestHandler, ResponseCompletionHandler
{
    private static final int TASK_TYPE_MAXLENGTH = 1024;

    protected final PollAndLockTasksDecoder requestDecoder = new PollAndLockTasksDecoder();
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final MessageHeaderEncoder headerEncoder = new MessageHeaderEncoder();
    protected final LockedTaskBatchEncoder responseEncoder = new LockedTaskBatchEncoder();
    protected final TaskInstanceEncoder taskInstanceEncoder = new TaskInstanceEncoder();

    protected final byte[] taskTypeBuff = new byte[TASK_TYPE_MAXLENGTH];

    protected final Log log;
    protected final Dispatcher logWriteBuffer;
    protected final LogScanner logScanner;
    protected final TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected final ClaimedFragment claimedLogFragment = new ClaimedFragment();

    public LockTaskBatchHandler(TaskQueueContext context)
    {
        log = context.getLog();
        logWriteBuffer = log.getWriteBuffer();
        logScanner = new LogScanner(context.getTaskInstanceIndex().getIndex());
    }

    @Override
    public int getTemplateId()
    {
        return requestDecoder.sbeTemplateId();
    }

    @Override
    public long onRequest(
            final DirectBuffer buffer,
            final int offset,
            final int length,
            final DeferredResponse response,
            final int sbeBlockLength,
            final int sbeSchemaVersion)
    {
        requestDecoder.wrap(buffer, offset, sbeBlockLength, sbeSchemaVersion);

        final long consumerId = requestDecoder.consumerId();
        final long lockTime = requestDecoder.lockTime();
//        final long maxTask = decoder.maxTasks();

        final int taskTypeLength = requestDecoder.taskTypeLength();
        requestDecoder.getTaskType(taskTypeBuff, 0, TASK_TYPE_MAXLENGTH);
        int taskTypeHash = TaskTypeHash.hashCode(taskTypeBuff, taskTypeLength);

        final long now = System.currentTimeMillis();
        final long lockTimeout = now + lockTime;

        // scan the log for tasks
        logScanner.init(now, taskTypeHash, taskTypeBuff, taskTypeLength);
        long pollPos = log.getInitialPosition();
        long lockableTaskPosition = -1;
        do
        {
            pollPos = log.pollFragment(pollPos, logScanner);
            lockableTaskPosition = logScanner.lockableTaskPosition;
        }
        while(pollPos > 0 && lockableTaskPosition == -1);

        if(lockableTaskPosition != -1)
        {
            try
            {
                lockTask(response, consumerId, lockTimeout, taskTypeLength);
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
                writeCommonResponse(response, consumerId, lockTimeout);
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
            final DeferredResponse response,
            final long consumerId,
            final long lockTimeout,
            final int taskTypeLength)
    {
        final TaskInstanceReader taskInstanceReader = logScanner.reader;
        final int payloadLength = taskInstanceReader.getPayloadLength();

        long claimedLogPosition = claimLogFragment(taskInstanceReader);
        // TODO: https://github.com/camunda-tngp/dispatcher/issues/5
        claimedLogPosition -= BitUtil.align(claimedLogFragment.getFragmentLength(), 8);

        if (claimedLogPosition >= 0)
        {
            writeLockedTaskInstanceLogEntry(claimedLogFragment, taskInstanceReader, consumerId, lockTimeout);

            if (response.allocate(lockTaskBatchResponseLength(taskTypeLength, payloadLength)))
            {
                writeCommonResponse(response, consumerId, lockTimeout);

                responseEncoder.tasksCount(1)
                    .next()
                        .taskId(taskInstanceReader.getDecoder().id())
                        .putPayload(taskInstanceReader.getReadBuffer(), taskInstanceReader.getPayloadOffset(), taskInstanceReader.getPayloadLength());

                response.defer(claimedLogPosition, this, null);
                claimedLogFragment.commit();
            }
        }
    }

    protected void writeCommonResponse(
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
            .version(responseEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        responseEncoder.wrap(responseBuffer, writeOffset)
            .consumerId(consumerId)
            .lockTime(lockTime);
    }

    protected long claimLogFragment(final TaskInstanceReader taskInstanceReader)
    {
        long claimedPosition = -1;

        do
        {
            claimedPosition = logWriteBuffer.claim(claimedLogFragment, taskInstanceReader.getLength());
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
            .version(taskInstanceEncoder.sbeSchemaVersion());

        writeOffset += headerEncoder.encodedLength();

        taskInstanceEncoder.wrap(writeBuffer, writeOffset);
        taskInstanceEncoder
            .id(decoder.id())
            .version(decoder.version() + 1)
            .state(TaskInstanceState.LOCKED)
            .lockOwnerId(consumerId)
            .lockTime(lockTimeout)
            .taskTypeHash(decoder.taskTypeHash());

        taskInstanceEncoder.putTaskType(reader.getReadBuffer(), reader.getTaskTypeOffset(), reader.getTaskTypeLength());
        taskInstanceEncoder.putPayload(reader.getReadBuffer(), reader.getPayloadOffset(), reader.getPayloadLength());
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

    static class LogScanner implements LogFragmentHandler
    {
        final HashIndex index;
        final TaskInstanceReader reader;

        long now;
        int taskTypeHashToPoll;
        byte[] taskTypeToPoll;
        int taskTypeToPollLength;
        byte[] taskType = new byte[TASK_TYPE_MAXLENGTH];

        long lockableTaskPosition;

        public LogScanner(HashIndex index)
        {
            this.index = index;
            reader = new TaskInstanceReader();
        }

        void init(long now, int taskTypeHashToPoll, byte[] taskTypeToPoll, int taskTypeToPollLength)
        {
            this.now = now;
            this.taskTypeHashToPoll = taskTypeHashToPoll;
            this.taskTypeToPoll = taskTypeToPoll;
            this.taskTypeToPollLength = taskTypeToPollLength;
            this.lockableTaskPosition = -1;
        }

        @Override
        public void onFragment(long position, FileChannel fileChannel, int offset, int length)
        {
            // TODO: only read header, block and taskType while scanning
            if(reader.read(fileChannel, offset, length))
            {
                final TaskInstanceDecoder decoder = reader.getDecoder();

                final long id = decoder.id();
                final TaskInstanceState state = decoder.state();
                final long lockTime = decoder.lockTime();

                if(decoder.taskTypeHash() == taskTypeHashToPoll
                        && (state == TaskInstanceState.NEW || (state == TaskInstanceState.LOCKED && lockTime < now)))
                {
                    final long lastPosition = index.getLong(id, -1);
                    if(lastPosition == position)
                    {
                        final int taskTypeLength = reader.getTaskTypeLength();
                        if(taskTypeToPollLength == taskTypeLength)
                        {
                            final DirectBuffer readBuffer = reader.getReadBuffer();
                            final int taskTypeOffset = reader.getTaskTypeOffset();

                            boolean taskTypeEqual = true;

                            for (int i = 0; i < taskTypeToPollLength && taskTypeEqual; i++)
                            {
                                taskTypeEqual &= taskTypeToPoll[i] == readBuffer.getByte(taskTypeOffset + i);
                            }

                            if(taskTypeEqual)
                            {
                                lockableTaskPosition = position;
                            }
                        }
                    }
                }
            }
            else
            {
                System.err.println("could not task instance at log position "+position);
            }
        }

    }
}
