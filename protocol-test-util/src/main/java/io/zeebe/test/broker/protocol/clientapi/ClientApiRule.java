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

import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.clientapi.ControlMessageType;
import io.zeebe.protocol.clientapi.ValueType;
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
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.collections.Int2ObjectHashMap;
import org.junit.rules.ExternalResource;

public class ClientApiRule extends ExternalResource {

  private static final String DEFAULT_WORKER = "defaultWorker";
  public static final long DEFAULT_LOCK_DURATION = 10000L;

  protected ClientTransport transport;

  protected final int nodeId;
  protected final Supplier<SocketAddress> brokerAddressSupplier;

  protected MsgPackHelper msgPackHelper;

  private final Int2ObjectHashMap<PartitionTestClient> testPartitionClients =
      new Int2ObjectHashMap<>();
  private final ControlledActorClock controlledActorClock = new ControlledActorClock();
  private ActorScheduler scheduler;

  protected int defaultPartitionId = -1;

  public ClientApiRule(final Supplier<SocketAddress> brokerAddressSupplier) {
    this.nodeId = 0;
    this.brokerAddressSupplier = brokerAddressSupplier;
  }

  public ClientApiRule(final int nodeId, final Supplier<SocketAddress> brokerAddressSupplier) {
    this.nodeId = nodeId;
    this.brokerAddressSupplier = brokerAddressSupplier;
  }

  @Override
  protected void before() throws Throwable {
    scheduler =
        ActorScheduler.newActorScheduler()
            .setCpuBoundActorThreadCount(1)
            .setActorClock(controlledActorClock)
            .build();
    scheduler.start();

    transport = Transports.newClientTransport("gateway").scheduler(scheduler).build();

    msgPackHelper = new MsgPackHelper();
    transport.registerEndpoint(nodeId, brokerAddressSupplier.get());

    final List<Integer> partitionIds = doRepeatedly(this::getPartitionIds).until(p -> !p.isEmpty());
    defaultPartitionId = partitionIds.get(0);
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

  public ControlMessageRequestBuilder createControlMessageRequest() {
    return new ControlMessageRequestBuilder(transport.getOutput(), nodeId, msgPackHelper);
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

  @SuppressWarnings("unchecked")
  public List<Integer> getPartitionIds() {
    try {
      final ControlMessageResponse response = requestTopology();

      final Map<String, Object> data = response.getData();
      final int partitionsCount = ((Long) data.get("partitionsCount")).intValue();

      return IntStream.range(0, partitionsCount).boxed().collect(Collectors.toList());
    } catch (final Exception e) {
      return Collections.EMPTY_LIST;
    }
  }

  public ControlMessageResponse requestTopology() {
    return createControlMessageRequest()
        .partitionId(Protocol.DEPLOYMENT_PARTITION)
        .messageType(ControlMessageType.REQUEST_TOPOLOGY)
        .data()
        .done()
        .sendAndAwait();
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
