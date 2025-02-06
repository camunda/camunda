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

import static io.atomix.raft.metrics.RaftRequestMetricsDoc.*;

import io.camunda.zeebe.util.collection.Table;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;

public class RaftRequestMetrics extends RaftMetrics {

  private final Map<String, Counter> raftMessagesReceived;
  private final MeterRegistry registry;
  private final Table<String, String, Counter> raftMessagesSend;

  public RaftRequestMetrics(final String partitionName, final MeterRegistry registry) {
    super(partitionName);
    raftMessagesReceived = new HashMap<>(32);
    raftMessagesSend = Table.simple();
    this.registry = registry;
  }

  public void receivedMessage(final String type) {
    getMessageReceived(type).increment();
  }

  public void sendMessage(final String memberId, final String type) {
    getMessageSent(memberId, type).increment();
  }

  private Counter getMessageReceived(final String type) {
    return raftMessagesReceived.computeIfAbsent(
        type,
        tpe ->
            Counter.builder(RAFT_MESSAGE_RECEIVED.getName())
                .description(RAFT_MESSAGE_RECEIVED.getDescription())
                .tags(RaftKeyNames.TYPE.asString(), RaftKeyNames.PARTITION_GROUP.asString())
                .register(registry));
  }

  private Counter getMessageSent(final String memberId, final String type) {
    return raftMessagesSend.computeIfAbsent(
        memberId,
        type,
        (member, tpe) ->
            Counter.builder(RAFT_MESSAGE_SEND.getName())
                .description(RAFT_MESSAGE_SEND.getDescription())
                .tags(
                    RaftKeyNames.TO.asString(),
                    member,
                    RaftKeyNames.TYPE.asString(),
                    tpe,
                    RaftKeyNames.PARTITION_GROUP.asString(),
                    partitionGroupName)
                .register(registry));
  }
}
