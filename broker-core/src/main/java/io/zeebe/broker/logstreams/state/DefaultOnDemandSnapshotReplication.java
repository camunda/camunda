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
package io.zeebe.broker.logstreams.state;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.logstreams.state.OnDemandSnapshotReplication;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class DefaultOnDemandSnapshotReplication
    implements OnDemandSnapshotReplication, AutoCloseable {

  private final ClusterCommunicationService communicationService;
  private final String requestReplicationTopic;
  private ExecutorService executorService;

  public DefaultOnDemandSnapshotReplication(
      ClusterCommunicationService communicationService,
      int partitionId,
      String name,
      ExecutorService executor) {
    this.communicationService = communicationService;
    requestReplicationTopic = String.format("snapshot-request-%d-%s", partitionId, name);
    this.executorService = executor;
  }

  @Override
  public void request(MemberId server) {
    communicationService.unicast(requestReplicationTopic, null, server);
  }

  @Override
  public void serve(Handler handler) {
    communicationService.subscribe(
        requestReplicationTopic,
        (Consumer<Void>) r -> handler.onSnapshotRequest(r),
        executorService);
  }

  @Override
  public void close() {
    communicationService.unsubscribe(requestReplicationTopic);
    executorService = null;
  }
}
