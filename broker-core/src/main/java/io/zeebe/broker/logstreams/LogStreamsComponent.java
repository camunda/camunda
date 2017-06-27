package io.zeebe.broker.logstreams;

import static io.zeebe.broker.logstreams.LogStreamServiceNames.LOG_STREAMS_MANAGER_SERVICE;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.SNAPSHOT_STORAGE_SERVICE;
import static io.zeebe.broker.system.SystemServiceNames.ACTOR_SCHEDULER_SERVICE;

import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;

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
            .dependency(TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME), topicSubscriptionService.getClientApiTransportInjector())
            .dependency(ACTOR_SCHEDULER_SERVICE, topicSubscriptionService.getActorSchedulerInjector())
            .groupReference(LogStreamServiceNames.LOG_STREAM_SERVICE_GROUP, topicSubscriptionService.getLogStreamsGroupReference())
            .install();
    }

}
