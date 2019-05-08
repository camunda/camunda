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
package io.zeebe.broker.logstreams.restore;

import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreClientFactory;
import io.zeebe.distributedlog.restore.RestoreServer;
import java.util.concurrent.ExecutorService;

public class BrokerRestoreFactory implements RestoreClientFactory {
  private final ClusterCommunicationService communicationService;

  public BrokerRestoreFactory(ClusterCommunicationService communicationService) {
    this.communicationService = communicationService;
  }

  @Override
  public RestoreClient createClient(int partitionId) {
    return new BrokerRestoreClient(
        communicationService, getReplicationTopic(partitionId), getRestoreInfoTopic(partitionId));
  }

  public RestoreServer createServer(int partitionId, ExecutorService executor) {
    return new BrokerRestoreServer(
        communicationService,
        getReplicationTopic(partitionId),
        getRestoreInfoTopic(partitionId),
        executor);
  }

  private String getReplicationTopic(int partitionId) {
    return String.format("log-replication-%d", partitionId);
  }

  private String getRestoreInfoTopic(int partitionId) {
    return String.format("restore-info-%d", partitionId);
  }
}
