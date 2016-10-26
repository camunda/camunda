package org.camunda.tngp.broker.taskqueue.request.handler;

import java.io.PrintStream;

import org.agrona.DirectBuffer;
import org.camunda.tngp.broker.taskqueue.TaskQueueContext;
import org.camunda.tngp.broker.taskqueue.subscription.AdhocTaskSubscription;
import org.camunda.tngp.broker.taskqueue.subscription.LockTasksOperator;
import org.camunda.tngp.broker.taskqueue.subscription.TaskSubscription;
import org.camunda.tngp.broker.transport.worker.spi.BrokerDataFrameHandler;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsDecoder;
import org.camunda.tngp.protocol.taskqueue.ProvideSubscriptionCreditsReader;

import uk.co.real_logic.sbe.PrimitiveValue;

public class ProvideSubscriptionCreditsHandler implements BrokerDataFrameHandler<TaskQueueContext>
{
    protected ProvideSubscriptionCreditsReader requestReader = new ProvideSubscriptionCreditsReader();

    @Override
    public int onDataFrame(TaskQueueContext context, DirectBuffer message, int offset, int length)
    {

        requestReader.wrap(message, offset, length);

        final long subscriptionId = requestReader.subscriptionId();
        final int consumerId = requestReader.consumerId();
        final long credits = requestReader.credits();

        if (subscriptionId == ProvideSubscriptionCreditsDecoder.subscriptionIdNullValue())
        {
            return ignoreRequest(System.out, "Subscription id not provided.");
        }
        if (consumerId == ProvideSubscriptionCreditsDecoder.consumerIdNullValue())
        {
            return ignoreRequest(System.out, "Consumer id not provided.");
        }

        final LockTasksOperator lockedTasksOperator = context.getLockedTasksOperator();

        final TaskSubscription subscription = lockedTasksOperator.getSubscription(requestReader.subscriptionId());

        if (subscription == null)
        {
            return ignoreRequest(System.out, "Subscription with id " + subscriptionId + " not registered.");
        }
        if (consumerId != subscription.getConsumerId())
        {
            return ignoreRequest(System.out, "Subscription " + subscriptionId + " not assigned to consumer " + consumerId);
        }
        if (subscription instanceof AdhocTaskSubscription)
        {
            return ignoreRequest(System.out, "Subscription " + subscriptionId + " is adhoc subscription");
        }

        if (credits < 0)
        {
            // note: credits should be positive due to protocol
            return ignoreRequest(System.err, "Ignoring credits request. Credits cannot be negative");
        }

        final long newCredits = subscription.getCredits() + credits;
        if (newCredits < subscription.getCredits() || newCredits > PrimitiveValue.MAX_VALUE_UINT32)
        {
            return ignoreRequest(System.err, "Amount " + credits + " lets credits overflow");
        }

        subscription.setCredits(newCredits);

        return 0;
    }

    protected int ignoreRequest(PrintStream outStream, String reason)
    {
        outStream.println("Ignoring credits request. " + reason);
        return 0;
    }

}
