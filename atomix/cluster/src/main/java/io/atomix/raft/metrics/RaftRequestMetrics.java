/*
 * Copyright 2016-present Open Networking Foundation
 * Copyright © 2020 camunda services GmbH (info@camunda.com)
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
package io.atomix.raft.metrics;

import io.prometheus.client.Counter;

public class RaftRequestMetrics extends RaftMetrics {

  private static final Counter RAFT_MESSAGES_RECEIVED =
      Counter.build()
          .namespace("atomix")
          .name("raft_messages_received")
          .help("Number of raft requests received")
          .labelNames("type", "partitionGroupName", "partition")
          .register();

  private static final Counter RAFT_MESSAGES_SEND =
      Counter.build()
          .namespace("atomix")
          .name("raft_messages_send")
          .help("Number of raft requests send")
          .labelNames("to", "type", "partitionGroupName", "partition")
          .register();

  public RaftRequestMetrics(final String partitionName) {
    super(partitionName);
  }

  public void receivedMessage(final String type) {
    RAFT_MESSAGES_RECEIVED.labels(type, partitionGroupName, partition).inc();
  }

  public void sendMessage(final String memberId, final String type) {
    RAFT_MESSAGES_SEND.labels(memberId, type, partitionGroupName, partition).inc();
  }
}
