package org.camunda.tngp.broker.taskqueue.request.handler;

import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.taskqueue.CreateTaskInstanceRequestReader;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.log.idgenerator.IdGenerator;
import org.camunda.tngp.taskqueue.data.TaskInstanceState;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

import org.agrona.DirectBuffer;

public class CreateTaskInstanceHandler implements BrokerRequestHandler<TaskQueueContext>
{
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();

    protected CreateTaskInstanceRequestReader requestReader = new CreateTaskInstanceRequestReader();

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

        final DirectBuffer payload = requestReader.getPayload();
        final DirectBuffer taskType = requestReader.getTaskType();

        taskInstanceWriter.id(taskInstanceId)
            .source(EventSource.API)
            .state(TaskInstanceState.NEW)
            .payload(payload, 0, payload.capacity())
            .taskType(taskType, 0, taskType.capacity());

        logWriter.write(taskInstanceWriter);

        return response.defer();
    }

}
