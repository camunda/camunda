package org.camunda.tngp.client.impl.cmd;

import java.util.Iterator;

import org.camunda.tngp.client.cmd.LockedTasksBatch;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder;
import org.camunda.tngp.protocol.taskqueue.MessageHeaderDecoder;
import org.camunda.tngp.protocol.taskqueue.LockedTaskBatchDecoder.TasksDecoder;

import org.agrona.DirectBuffer;

public class PollAndLockResponseHandler implements ClientResponseHandler<LockedTasksBatch>
{
    protected final MessageHeaderDecoder headerDecoder = new MessageHeaderDecoder();
    protected final LockedTaskBatchDecoder responseDecoder = new LockedTaskBatchDecoder();

    @Override
    public int getResponseSchemaId()
    {
        return LockedTaskBatchDecoder.SCHEMA_ID;
    }

    @Override
    public int getResponseTemplateId()
    {
        return LockedTaskBatchDecoder.TEMPLATE_ID;
    }

    @Override
    public LockedTasksBatch readResponse(DirectBuffer responseBuffer, int offset, int length)
    {
        final LockedTasksBatchImpl lockedTasksBatch = new LockedTasksBatchImpl();

        headerDecoder.wrap(responseBuffer, offset);

        offset += headerDecoder.encodedLength();

        responseDecoder.wrap(responseBuffer, offset, headerDecoder.blockLength(), headerDecoder.version());

        lockedTasksBatch.setLockTime(responseDecoder.lockTime());

        final Iterator<TasksDecoder> taskIterator = responseDecoder.tasks().iterator();
        while (taskIterator.hasNext())
        {
            final TasksDecoder taskDecoder = taskIterator.next();
            final int payloadLength = taskDecoder.payloadLength();

            final LockedTaskImpl lockedTask = new LockedTaskImpl(payloadLength);

            lockedTask.setId(taskDecoder.taskId());

            final long wfInstanceId = taskDecoder.wfInstanceId();
            final Long apiWfInstanceId = (wfInstanceId != TasksDecoder.wfInstanceIdNullValue()) ? wfInstanceId : null;

            lockedTask.setWorkflowInstanceId(apiWfInstanceId);
            taskDecoder.getPayload(lockedTask.getPayloadBuffer(), 0, payloadLength);

            lockedTasksBatch.addTask(lockedTask);
        }

        return lockedTasksBatch;
    }


}
