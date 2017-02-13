package org.camunda.tngp.broker.event;

import org.camunda.tngp.broker.event.processor.TopicSubscriptionManager;
import org.camunda.tngp.logstreams.processor.StreamProcessorController;
import org.camunda.tngp.servicecontainer.ServiceName;

public class TopicSubscriptionNames
{

    public static final ServiceName<TopicSubscriptionManager> TOPIC_SUBSCRIPTION_MANAGER = ServiceName.newServiceName("log.subscription.manager", TopicSubscriptionManager.class);;

    public static ServiceName<StreamProcessorController> subscriptionServiceName(String logStreamName, long id)
    {
        return ServiceName.newServiceName(String.format("log.%s.subscription.%d", logStreamName, id), StreamProcessorController.class);
    }
}
