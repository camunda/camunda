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

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_SYSTEM_GROUP_NAME;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;

import io.zeebe.broker.event.TopicSubscriptionServiceNames;
import io.zeebe.broker.event.processor.TopicSubscriptionService;
import io.zeebe.broker.logstreams.processor.StreamProcessorServiceFactory;
import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.broker.transport.TransportServiceNames;
import io.zeebe.servicecontainer.ServiceContainer;
import io.zeebe.util.DurationUtil;
import java.time.Duration;

public class LogStreamsComponent implements Component {
  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final TopicSubscriptionService topicSubscriptionService =
        new TopicSubscriptionService(serviceContainer);
    serviceContainer
        .createService(
            TopicSubscriptionServiceNames.TOPIC_SUBSCRIPTION_SERVICE, topicSubscriptionService)
        .dependency(
            TransportServiceNames.serverTransport(TransportServiceNames.CLIENT_API_SERVER_NAME),
            topicSubscriptionService.getClientApiTransportInjector())
        .dependency(
            LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY,
            topicSubscriptionService.getStreamProcessorServiceFactoryInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, topicSubscriptionService.getPartitionsGroupReference())
        .groupReference(
            LEADER_PARTITION_SYSTEM_GROUP_NAME,
            topicSubscriptionService.getSystemPartitionGroupReference())
        .install();

    final Duration snapshotPeriod =
        DurationUtil.parse(context.getBrokerConfiguration().getData().getSnapshotPeriod());
    final StreamProcessorServiceFactory streamProcessorFactory =
        new StreamProcessorServiceFactory(serviceContainer, snapshotPeriod);
    serviceContainer
        .createService(STREAM_PROCESSOR_SERVICE_FACTORY, streamProcessorFactory)
        .install();
  }
}
