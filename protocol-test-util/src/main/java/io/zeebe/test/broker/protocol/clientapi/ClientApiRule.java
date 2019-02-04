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
package io.zeebe.test.broker.protocol.clientapi;

import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.data.cluster.BrokerInfo;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.agrona.collections.Int2ObjectHashMap;
import org.junit.rules.ExternalResource;

public class ClientApiRule extends ExternalResource {

  public static final long DEFAULT_LOCK_DURATION = 10000L;
  private static final String DEFAULT_WORKER = "defaultWorker";

  protected final int nodeId;
  protected final Supplier<AtomixCluster> atomixSupplier;
  private final Int2ObjectHashMap<PartitionTestClient> testPartitionClients =
      new Int2ObjectHashMap<>();
  private final ControlledActorClock controlledActorClock = new ControlledActorClock();
  protected ClientTransport transport;
  protected MsgPackHelper msgPackHelper;
  protected int defaultPartitionId = -1;
  private AtomixCluster atomix;
  private ActorScheduler scheduler;

  public ClientApiRule(final Supplier<AtomixCluster> atomixSupplier) {
    this(0, atomixSupplier);
  }

  public ClientApiRule(final int nodeId, final Supplier<AtomixCluster> atomixSupplier) {
    this.nodeId = nodeId;
    this.atomixSupplier = atomixSupplier;
  }

  @Override
  protected void before() {
    fetchAtomix();

    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setActorClock(controlledActorClock)
            .build();
    scheduler.start();

    transport = Transports.newClientTransport("gateway").scheduler(scheduler).build();
    msgPackHelper = new MsgPackHelper();

    waitForTopology();
    getBrokerInfoStream()
        .forEach(
            brokerInfo ->
                transport.registerEndpoint(
                    brokerInfo.getNodeId(),
                    SocketAddress.from(brokerInfo.getApiAddress(BrokerInfo.CLIENT_API_PROPERTY))));

    final List<Integer> partitionIds = doRepeatedly(this::getPartitionIds).until(p -> !p.isEmpty());
    defaultPartitionId = partitionIds.get(0);
  }

  public void restart() {
    fetchAtomix();
  }

  private void fetchAtomix() {
    atomix = atomixSupplier.get();
    assertThat(atomix).isNotNull();
  }

  private void waitForTopology() {
    waitUntil(() -> getBrokerInfoStream().count() > 0);
  }

  @Override
  protected void after() {
    if (transport != null) {
      transport.close();
    }

    if (scheduler != null) {
      scheduler.stop();
    }
  }

  /** targets the default partition by default */
  public ExecuteCommandRequestBuilder createCmdRequest() {
    return new ExecuteCommandRequestBuilder(transport.getOutput(), nodeId, msgPackHelper)
        .partitionId(defaultPartitionId);
  }

  /** targets the default partition by default */
  public ExecuteCommandRequestBuilder createCmdRequest(int partition) {
    return new ExecuteCommandRequestBuilder(transport.getOutput(), nodeId, msgPackHelper)
        .partitionId(partition);
  }

  public PartitionTestClient partitionClient() {
    return partitionClient(defaultPartitionId);
  }

  public PartitionTestClient partitionClient(final int partitionId) {
    if (!testPartitionClients.containsKey(partitionId)) {
      testPartitionClients.put(partitionId, new PartitionTestClient(this, partitionId));
    }
    return testPartitionClients.get(partitionId);
  }

  public ExecuteCommandRequest activateJobs(final String type) {
    return activateJobs(defaultPartitionId, type, DEFAULT_LOCK_DURATION);
  }

  public ExecuteCommandRequest activateJobs(
      final int partitionId, final String type, final long lockDuration, final int amount) {
    // to make sure that job already exist
    partitionClient(partitionId)
        .receiveJobs()
        .withIntent(JobIntent.CREATED)
        .withType(type)
        .getFirst();

    return createCmdRequest(partitionId)
        .type(ValueType.JOB_BATCH, JobBatchIntent.ACTIVATE)
        .command()
        .put("type", type)
        .put("worker", DEFAULT_WORKER)
        .put("timeout", lockDuration)
        .put("amount", amount)
        .put("jobs", Collections.emptyList())
        .done()
        .send();
  }

  public ExecuteCommandRequest activateJobs(
      final int partitionId, final String type, final long lockDuration) {
    return activateJobs(partitionId, type, lockDuration, 10);
  }

  public void waitForPartition(final int partitions) {
    waitUntil(() -> getPartitionIds().size() >= partitions);
  }

  public List<Integer> getPartitionIds() {
    return getBrokerInfoStream()
        .findFirst()
        .map(
            brokerInfo ->
                IntStream.range(0, brokerInfo.getPartitionsCount())
                    .boxed()
                    .collect(Collectors.toList()))
        .orElse(Collections.emptyList());
  }

  private Stream<BrokerInfo> getBrokerInfoStream() {
    return atomix.getMembershipService().getMembers().stream()
        .map(Member::properties)
        .map(BrokerInfo::fromProperties)
        .filter(Objects::nonNull);
  }

  public int getDefaultPartitionId() {
    return defaultPartitionId;
  }

  public ClientTransport getTransport() {
    return transport;
  }

  public ControlledActorClock getClock() {
    return controlledActorClock;
  }
}
