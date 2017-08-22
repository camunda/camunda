/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
            .groupReference(LogStreamServiceNames.WORKFLOW_STREAM_GROUP, topicSubscriptionService.getLogStreamsGroupReference())
            .install();
    }

}
