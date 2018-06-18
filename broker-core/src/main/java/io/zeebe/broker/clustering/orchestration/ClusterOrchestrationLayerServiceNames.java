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
package io.zeebe.broker.clustering.orchestration;

import io.zeebe.broker.clustering.orchestration.id.IdGenerator;
import io.zeebe.broker.clustering.orchestration.state.KnownTopics;
import io.zeebe.broker.clustering.orchestration.topic.ReplicationFactorService;
import io.zeebe.broker.clustering.orchestration.topic.RequestPartitionsMessageHandler;
import io.zeebe.broker.clustering.orchestration.topic.TopicCreationService;
import io.zeebe.servicecontainer.ServiceName;

public class ClusterOrchestrationLayerServiceNames {

  public static final ServiceName<Void> CLUSTER_ORCHESTRATION_INSTALL_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.install", Void.class);
  public static final ServiceName<Void> CLUSTER_ORCHESTRATION_COMPOSITE_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.composite", Void.class);

  public static final ServiceName<KnownTopics> KNOWN_TOPICS_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.knownTopics", KnownTopics.class);

  public static final ServiceName<TopicCreationService> TOPIC_CREATION_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.topicCreation", TopicCreationService.class);
  public static final ServiceName<ReplicationFactorService> REPLICATION_FACTOR_SERVICE_NAME =
      ServiceName.newServiceName(
          "cluster.orchestration.replicationFactor", ReplicationFactorService.class);
  public static final ServiceName<RequestPartitionsMessageHandler>
      REQUEST_PARTITIONS_MESSAGE_HANDLER_SERVICE_NAME =
          ServiceName.newServiceName(
              "cluster.orchestration.requestsPartitionsMessageHandler",
              RequestPartitionsMessageHandler.class);
  public static final ServiceName<IdGenerator> ID_GENERATOR_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.idGenerator", IdGenerator.class);
  public static final ServiceName<NodeSelector> NODE_SELECTOR_SERVICE_NAME =
      ServiceName.newServiceName("cluster.orchestration.nodeSelector", NodeSelector.class);
}
