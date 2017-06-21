package org.camunda.tngp.broker.logstreams;

import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.LOG_STREAMS_MANAGER_SERVICE;
import static org.camunda.tngp.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static org.camunda.tngp.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import org.camunda.tngp.broker.event.TopicSubscriptionServiceNames;
import org.camunda.tngp.broker.event.processor.TopicSubscriptionService;
import org.camunda.tngp.broker.system.Component;
import org.camunda.tngp.broker.system.SystemContext;
import org.camunda.tngp.broker.transport.TransportServiceNames;

public class LogStreamsComponent implements Component
{

    @Override
    public void init(SystemContext context)
    {
        final LogStreamsManagerService streamsManager = new LogStreamsManagerService(context.getConfigurationManager());
        context.getServiceContainer().createService(LOG_STREAMS_MANAGER_SERVICE, streamsManager)
            .dependency(ACTOR_SCHEDULER_SERVICE, streamsManager.getActorSchedulerInjector())
            .install();

        final SnapshotStorageService snapshotStorageService = new SnapshotStorageService(context.getConfigurationManager());
        context.getServiceContainer().createService(SNAPSHOT_STORAGE_SERVICE, snapshotStorageService)
            .install();

        final TopicSubscriptionService topicSubscriptionService = new TopicSubscriptionService(context.getConfigurationManager());
        context.getServiceContainer()
            .createService(TopicSubscriptionServiceNames.TOPIC_SUBSCRIPTION_SERVICE, topicSubscriptionService)
            .dependency(TransportServiceNames.TRANSPORT_SEND_BUFFER, topicSubscriptionService.getSendBufferInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, topicSubscriptionService.getActorSchedulerInjector())
            .groupReference(LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP, topicSubscriptionService.getLogStreamsGroupReference())
            .install();
    }

}
