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

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.ClusterCommunicationService;
import io.zeebe.distributedlog.restore.RestoreClient;
import io.zeebe.distributedlog.restore.RestoreInfoRequest;
import io.zeebe.distributedlog.restore.RestoreInfoResponse;
import io.zeebe.distributedlog.restore.log.LogReplicationRequest;
import io.zeebe.distributedlog.restore.log.LogReplicationResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class BrokerRestoreClient implements RestoreClient {
  private final ClusterCommunicationService communicationService;
  private final String logReplicationTopic;
  private final String restoreInfoTopic;

  public BrokerRestoreClient(
      ClusterCommunicationService communicationService,
      String logReplicationTopic,
      String restoreInfoTopic) {
    this.communicationService = communicationService;
    this.logReplicationTopic = logReplicationTopic;
    this.restoreInfoTopic = restoreInfoTopic;
  }

  @Override
  public CompletableFuture<LogReplicationResponse> replicate(
      MemberId server, LogReplicationRequest request) {
    return communicationService.send(
        logReplicationTopic,
        request,
        SbeLogReplicationRequest::serialize,
        SbeLogReplicationResponse::new,
        server,
        Duration.ofSeconds(5));
  }

  @Override
  public CompletableFuture<RestoreInfoResponse> requestRestoreInfo(
      MemberId server, RestoreInfoRequest request) {
    return communicationService.send(
        restoreInfoTopic,
        request,
        SbeRestoreInfoRequest::serialize,
        SbeRestoreInfoResponse::new,
        server,
        Duration.ofSeconds(5));
  }
}
