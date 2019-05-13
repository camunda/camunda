/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.distributedlog.restore;

import io.atomix.cluster.MemberId;

public class RestoreStrategy {
  private final MemberId restoreServer;
  private final ReplicationTarget replicationTarget;

  public RestoreStrategy(MemberId restoreServer, ReplicationTarget replicationTarget) {
    this.restoreServer = restoreServer;
    this.replicationTarget = replicationTarget;
  }

  public MemberId getRestoreServer() {
    return restoreServer;
  }

  public ReplicationTarget getReplicationTarget() {
    return replicationTarget;
  }

  public enum ReplicationTarget {
    SNAPSHOT,
    EVENTS,
    NONE,
  }
}
