package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.log.LogEntryHeaderReader.EventSource;
import org.camunda.tngp.broker.log.LogWriter;
import org.camunda.tngp.broker.taskqueue.CompleteTaskRequestReader;
import org.camunda.tngp.broker.taskqueue.SingleTaskAckResponseWriter;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskInstanceWriter;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.log.TaskInstanceRequestWriter;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.log.BufferedLogReader;
import org.camunda.tngp.log.LogReader;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskDecoder;
import org.camunda.tngp.protocol.taskqueue.CompleteTaskEncoder;
import org.camunda.tngp.protocol.taskqueue.TaskInstanceReader;
import org.camunda.tngp.protocol.log.TaskInstanceRequestType;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class CompleteTaskHandler implements BrokerRequestHandler<TaskQueueContext>
{
    protected CompleteTaskRequestReader requestReader = new CompleteTaskRequestReader();
    protected SingleTaskAckResponseWriter responseWriter = new SingleTaskAckResponseWriter();

    protected TaskInstanceReader taskInstanceReader = new TaskInstanceReader();
    protected TaskInstanceWriter taskInstanceWriter = new TaskInstanceWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    protected TaskInstanceRequestWriter logRequestWriter = new TaskInstanceRequestWriter();

    protected static final int READ_BUFFER_SIZE = 1024 * 1024;
    protected LogReader logReader = new BufferedLogReader();

    @Override
    public long onRequest(
            final TaskQueueContext ctx,
            final DirectBuffer msg,
            final int offset,
            final int length,
            final DeferredResponse response)
    {
        final LogWriter logWriter = ctx.getLogWriter();

        requestReader.wrap(msg, offset, length);

        final int consumerId = requestReader.consumerId();
        if (consumerId == CompleteTaskEncoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id is required");
        }

        final long taskId = requestReader.taskId();
        if (taskId == CompleteTaskEncoder.taskIdNullValue())
        {
            return writeError(response, "Task id is required");
        }

        final DirectBuffer payload = requestReader.getPayload();

        logRequestWriter
            .type(TaskInstanceRequestType.COMPLETE)
            .key(taskId)
            .lockOwnerId(consumerId)
            .payload(payload, 0, payload.capacity())
            .source(EventSource.API);

        logWriter.write(logRequestWriter);

        return response.deferFifo();
    }

    protected int writeError(DeferredResponse response, String errorMessage)
    {
        errorWriter
            .componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(TaskErrors.COMPLETE_TASK_ERROR)
            .errorMessage(errorMessage);

        if (response.allocateAndWrite(errorWriter))
        {
            response.commit();
            return 0;
        }
        else
        {
            return -1;
        }
    }

    @Override
    public int getTemplateId()
    {
        return CompleteTaskDecoder.TEMPLATE_ID;
    }

}
