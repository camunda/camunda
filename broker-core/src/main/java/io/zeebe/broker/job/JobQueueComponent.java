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
package io.zeebe.broker.job;

import static io.zeebe.broker.clustering.base.ClusterBaseLayerServiceNames.LEADER_PARTITION_GROUP_NAME;
import static io.zeebe.broker.job.JobQueueServiceNames.JOB_QUEUE_MANAGER;
import static io.zeebe.broker.job.JobQueueServiceNames.JOB_QUEUE_SUBSCRIPTION_MANAGER;
import static io.zeebe.broker.logstreams.LogStreamServiceNames.STREAM_PROCESSOR_SERVICE_FACTORY;
import static io.zeebe.broker.transport.TransportServiceNames.CLIENT_API_SERVER_NAME;
import static io.zeebe.broker.transport.TransportServiceNames.serverTransport;

import io.zeebe.broker.system.Component;
import io.zeebe.broker.system.SystemContext;
import io.zeebe.servicecontainer.ServiceContainer;

public class JobQueueComponent implements Component {
  @Override
  public void init(SystemContext context) {
    final ServiceContainer serviceContainer = context.getServiceContainer();

    final JobSubscriptionManagerService jobSubscriptionManagerService =
        new JobSubscriptionManagerService(serviceContainer);
    serviceContainer
        .createService(JOB_QUEUE_SUBSCRIPTION_MANAGER, jobSubscriptionManagerService)
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME),
            jobSubscriptionManagerService.getClientApiTransportInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            jobSubscriptionManagerService.getStreamProcessorServiceFactoryInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME,
            jobSubscriptionManagerService.getLeaderPartitionsGroupReference())
        .install();

    final JobQueueManagerService jobQueueManagerService = new JobQueueManagerService();
    serviceContainer
        .createService(JOB_QUEUE_MANAGER, jobQueueManagerService)
        .dependency(
            serverTransport(CLIENT_API_SERVER_NAME),
            jobQueueManagerService.getClientApiTransportInjector())
        .dependency(
            JOB_QUEUE_SUBSCRIPTION_MANAGER,
            jobQueueManagerService.getJobSubscriptionManagerInjector())
        .dependency(
            STREAM_PROCESSOR_SERVICE_FACTORY,
            jobQueueManagerService.getStreamProcessorServiceFactoryInjector())
        .groupReference(
            LEADER_PARTITION_GROUP_NAME, jobQueueManagerService.getPartitionsGroupReference())
        .install();
  }
}
