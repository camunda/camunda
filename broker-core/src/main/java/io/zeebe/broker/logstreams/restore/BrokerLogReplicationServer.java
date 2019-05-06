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
import io.atomix.utils.concurrent.SingleThreadContext;
import io.atomix.utils.concurrent.ThreadContext;
import io.zeebe.distributedlog.restore.LogReplicationServer;
import java.util.concurrent.CompletableFuture;

public class BrokerLogReplicationServer implements AutoCloseable, LogReplicationServer {
  private final ClusterCommunicationService communicationService;
  private final String replicationTopic;
  private final ThreadContext threadContext;

  public BrokerLogReplicationServer(
      ClusterCommunicationService communicationService, String replicationTopic) {
    this(
        communicationService,
        replicationTopic,
        new SingleThreadContext(String.format("%s-%%d", replicationTopic)));
  }

  public BrokerLogReplicationServer(
      ClusterCommunicationService communicationService,
      String replicationTopic,
      ThreadContext threadContext) {
    this.communicationService = communicationService;
    this.replicationTopic = replicationTopic;
    this.threadContext = threadContext;
  }

  @Override
  public void close() {
    communicationService.unsubscribe(replicationTopic);
    threadContext.close();
  }

  @Override
  public CompletableFuture<Void> serve(Handler server) {
    return communicationService.subscribe(
        replicationTopic,
        SbeLogReplicationRequest::new,
        server::onReplicationRequest,
        SbeLogReplicationResponse::serialize,
        threadContext);
  }
}
