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
package io.zeebe.distributedlog;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.servicecontainer.testing.ServiceContainerRule;
import io.zeebe.test.util.TestUtil;
import io.zeebe.util.sched.testing.ActorSchedulerRule;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;

public class DistributedLogMultiplePartitionsTest {

  public ActorSchedulerRule actorSchedulerRule1 = new ActorSchedulerRule();
  public ActorSchedulerRule actorSchedulerRule2 = new ActorSchedulerRule();
  public ActorSchedulerRule actorSchedulerRule3 = new ActorSchedulerRule();

  public ServiceContainerRule serviceContainerRule1 = new ServiceContainerRule(actorSchedulerRule1);

  public ServiceContainerRule serviceContainerRule2 = new ServiceContainerRule(actorSchedulerRule2);

  public ServiceContainerRule serviceContainerRule3 = new ServiceContainerRule(actorSchedulerRule3);

  private static final List<String> MEMBERS = Arrays.asList("1", "2", "3");

  public DistributedLogRule node1 =
      new DistributedLogRule(
          serviceContainerRule1, 1, NUM_PARTITIONS, REPLICATION_FACTOR, MEMBERS, null);

  public DistributedLogRule node2 =
      new DistributedLogRule(
          serviceContainerRule2,
          2,
          NUM_PARTITIONS,
          REPLICATION_FACTOR,
          MEMBERS,
          Collections.singletonList(node1.getNode()));

  public DistributedLogRule node3 =
      new DistributedLogRule(
          serviceContainerRule3,
          3,
          NUM_PARTITIONS,
          REPLICATION_FACTOR,
          MEMBERS,
          Collections.singletonList(node2.getNode()));

  public Timeout timeoutRule = Timeout.seconds(60);

  public static final int DEFAULT_RETRIES = 500;

  private static final int NUM_PARTITIONS = 3;
  private static final int REPLICATION_FACTOR = 3;

  @Rule
  public RuleChain ruleChain =
      RuleChain.outerRule(timeoutRule)
          .around(actorSchedulerRule1)
          .around(serviceContainerRule1)
          .around(actorSchedulerRule2)
          .around(serviceContainerRule2)
          .around(actorSchedulerRule3)
          .around(serviceContainerRule3)
          .around(node1)
          .around(node2)
          .around(node3);

  @Test
  public void shouldReplicateOnCorrectPartitions()
      throws ExecutionException, InterruptedException, TimeoutException {

    node1.waitUntilNodesJoined();
    node2.waitUntilNodesJoined();
    node3.waitUntilNodesJoined();

    // when

    writeEventAndWaitUntilReplicated(START_PARTITION_ID, node1, "record1");
    writeEventAndWaitUntilReplicated(START_PARTITION_ID + 1, node2, "record2");
    writeEventAndWaitUntilReplicated(START_PARTITION_ID + 2, node3, "record3");

    // then
    assertEventsCount(START_PARTITION_ID, node1, 1);
    assertEventsCount(START_PARTITION_ID + 1, node1, 1);
    assertEventsCount(START_PARTITION_ID + 2, node1, 1);

    assertEventsCount(START_PARTITION_ID + 0, node2, 1);
    assertEventsCount(START_PARTITION_ID + 1, node2, 1);
    assertEventsCount(START_PARTITION_ID + 2, node2, 1);

    assertEventsCount(START_PARTITION_ID + 0, node3, 1);
    assertEventsCount(START_PARTITION_ID + 1, node3, 1);
    assertEventsCount(START_PARTITION_ID + 2, node3, 1);
  }

  private void writeEventAndWaitUntilReplicated(
      int partitionId, DistributedLogRule leaderNode, String message) {
    leaderNode.becomeLeader(partitionId);
    final Event event1 = writeEvent(partitionId, leaderNode, message);
    assertEventReplicated(partitionId, event1);
    leaderNode.becomeFollower(partitionId);
  }

  private Event writeEvent(int partitionId, DistributedLogRule leaderNode, String message) {
    final Event event = new Event();
    event.message = message;
    event.position = leaderNode.writeEvent(partitionId, message);
    return event;
  }

  private class Event {
    String message;
    long position;
  }

  private void assertEventReplicated(int partitionId, Event event) {
    TestUtil.waitUntil(
        () -> node1.eventAppended(partitionId, event.message, event.position), DEFAULT_RETRIES);
    TestUtil.waitUntil(
        () -> node2.eventAppended(partitionId, event.message, event.position), DEFAULT_RETRIES);
    TestUtil.waitUntil(
        () -> node3.eventAppended(partitionId, event.message, event.position), DEFAULT_RETRIES);
  }

  private void assertEventsCount(int partitionId, DistributedLogRule node, int expectedCount) {
    assertThat(node.getCommittedEventsCount(partitionId)).isEqualTo(expectedCount);
  }
}
