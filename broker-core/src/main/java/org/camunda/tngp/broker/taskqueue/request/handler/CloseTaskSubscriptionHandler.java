package org.camunda.tngp.broker.taskqueue.request.handler;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.taskqueue.TaskErrors;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscription;
import org.camunda.tngp.broker.transport.worker.spi.BrokerRequestHandler;
import org.camunda.tngp.protocol.error.ErrorWriter;
import org.camunda.tngp.protocol.taskqueue.CloseTaskSubscriptionDecoder;
import org.camunda.tngp.protocol.taskqueue.CloseTaskSubscriptionRequestReader;
import org.camunda.tngp.protocol.taskqueue.TaskSubscriptionAckWriter;
import org.camunda.tngp.transport.requestresponse.server.DeferredResponse;

public class CloseTaskSubscriptionHandler implements BrokerRequestHandler<TaskQueueContext>
{

    protected CloseTaskSubscriptionRequestReader requestReader = new CloseTaskSubscriptionRequestReader();
    protected TaskSubscriptionAckWriter responseWriter = new TaskSubscriptionAckWriter();
    protected ErrorWriter errorWriter = new ErrorWriter();

    @Override
    public long onRequest(TaskQueueContext context, DirectBuffer msg, int offset, int length, DeferredResponse response)
    {
        requestReader.wrap(msg, offset, length);

        final long subscriptionId = requestReader.subscriptionId();
        final int consumerId = requestReader.consumerId();

        if (subscriptionId == CloseTaskSubscriptionDecoder.subscriptionIdNullValue())
        {
            return writeError(response, "Subscription id not provided");
        }
        if (consumerId == CloseTaskSubscriptionDecoder.consumerIdNullValue())
        {
            return writeError(response, "Consumer id not provided");
        }

        final LockTasksOperator lockTasksOperator = context.getLockedTasksOperator();
        final TaskSubscription subscription = lockTasksOperator.getSubscription(subscriptionId);

        if (subscription == null)
        {
            return writeError(response, "Subscription does not exist");
        }

        if (subscription.getConsumerId() == consumerId)
        {
            lockTasksOperator.removeSubscription(subscription);
            return writeAck(response, subscriptionId);
        }
        else
        {
            return writeError(response, "Subscription does not belong to provided consumer");
        }
    }

    protected int writeError(DeferredResponse response, String message)
    {
        errorWriter.componentCode(TaskErrors.COMPONENT_CODE)
            .detailCode(TaskErrors.CLOSE_SUBSCRIPTION_ERROR)
            .errorMessage(message);
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

    protected int writeAck(DeferredResponse response, long subscriptionId)
    {
        responseWriter.id(subscriptionId);

        if (response.allocateAndWrite(responseWriter))
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
        return CloseTaskSubscriptionDecoder.TEMPLATE_ID;
    }

}
