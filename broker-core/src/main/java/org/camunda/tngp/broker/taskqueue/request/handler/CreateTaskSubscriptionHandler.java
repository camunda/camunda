package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscription;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionDecoder;
import org.camunda.tngp.protocol.taskqueue.CreateTaskSubscriptionRequestReader;
import org.camunda.tngp.protocol.taskqueue.TaskSubscriptionAckWriter;
import org.camunda.tngp.protocol.wf.Constants;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class CreateTaskSubscriptionHandler implements BrokerRequestHandler<TaskQueueContext>
{

    protected CreateTaskSubscriptionRequestReader requestReader = new CreateTaskSubscriptionRequestReader();
    protected TaskSubscriptionAckWriter subscriptionWriter = new TaskSubscriptionAckWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(TaskQueueContext context, DirectBuffer msg, int offset, int length, DeferredResponse response)
    {
        requestReader.wrap(msg, offset, length);

        if (requestReader.consumerId() == CreateTaskSubscriptionDecoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id not provided");
        }
        if (requestReader.lockDuration() == CreateTaskSubscriptionDecoder.lockDurationNullValue())
        {
            return writeError(response, "Lock duration not provided");
        }
        if (requestReader.initialCredits() == CreateTaskSubscriptionDecoder.initialCreditsNullValue())
        {
            return writeError(response, "Initial credits not provided");
        }
        if (requestReader.taskType().capacity() > Constants.TASK_TYPE_MAX_LENGTH)
        {
            return writeError(response, "Provided task type exceeds maximum length " + Constants.TASK_TYPE_MAX_LENGTH);
        }

        final LockTasksOperator lockedTasksOperator = context.getLockedTasksOperator();

        final TaskSubscription subscription = lockedTasksOperator.openSubscription(
                response.getChannelId(),
                requestReader.consumerId(),
                requestReader.lockDuration(),
                requestReader.initialCredits(),
                requestReader.taskType());

        return writeSubscription(response, subscription);
    }

    protected int writeError(DeferredResponse response, String message)
    {
        errorWriter.componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(TaskErrors.CREATE_SUBSCRIPTION_ERROR)
            .errorMessage(message);

        if (response.allocateAndWrite(errorWriter))
        {
            response.commit();
            return 0;
        }
        else
        {
            response.abort();
            return -1;
        }
    }

    protected int writeSubscription(DeferredResponse response, TaskSubscription subscription)
    {
        subscriptionWriter.id(subscription.getId());

        if (response.allocateAndWrite(subscriptionWriter))
        {
            response.commit();
            return 0;
        }
        else
        {
            response.abort();
            return -1;
        }
    }

    @Override
    public int getTemplateId()
    {
        return CreateTaskSubscriptionDecoder.TEMPLATE_ID;
    }

}
