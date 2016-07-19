package org.camunda.tngp.broker.taskqueue.handler;

import org.camunda.tngp.broker.taskqueue.SingleTaskAckResponseWriter;
import org.camunda.tngp.broker.taskqueue.CreateTaskInstanceRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.log.LogWriter;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;
import org.camunda.tngp.transport.requestresponse.server.ResponseCompletionHandler;

import uk.co.real_logic.agrona.DirectBuffer;

public class CreateTaskInstanceHandler implements BrokerRequestHandler<TaskQueueContext>, ResponseCompletionHandler
{
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    protected CreateTaskInstanceRequestReader requestReader = new CreateTaskInstanceRequestReader();
    protected SingleTaskAckResponseWriter responseWriter = new SingleTaskAckResponseWriter();

    public long onRequest(
        final TaskQueueContext ctx,
        final DirectBuffer msg,
        final int offset,
        final int length,
        final DeferredResponse response)
    {
        final IdGenerator taskInstanceIdGenerator = ctx.getTaskInstanceIdGenerator();
        final LogWriter logWriter = ctx.getLogWriter();

        requestReader.wrap(msg, offset, length);

        // TODO: validate request (e.g. task type length)

        final long taskInstanceId = taskInstanceIdGenerator.nextId();
        responseWriter.taskId(taskInstanceId);

        if (response.allocateAndWrite(responseWriter))
        {
            final DirectBuffer payload = requestReader.getPayload();
            final DirectBuffer taskType = requestReader.getTaskType();

            taskInstanceWriter.id(taskInstanceId)
                .state(TaskInstanceState.NEW)
                .payload(payload, 0, payload.capacity())
                .taskType(taskType, 0, taskType.capacity());

            final long logEntryOffset = logWriter.write(taskInstanceWriter);

            return response.defer(logEntryOffset, this);
        }
        else
        {
            // TODO: could not allocate response; backpressure
            return -1L;
        }
    }

    @Override
    public void onAsyncWorkCompleted(final DeferredResponse response)
    {
        response.commit();
    }

    @Override
    public void onAsyncWorkFailed(final DeferredResponse response)
    {
        response.abort();
    }
}
