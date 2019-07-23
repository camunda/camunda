/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test.broker.protocol.commandapi;

import static io.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.zeebe.test.util.TestUtil.doRepeatedly;
import static io.zeebe.test.util.TestUtil.waitUntil;
import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.AtomixCluster;
import io.atomix.cluster.Member;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.SubscriptionUtil;
import io.zeebe.protocol.impl.encoding.BrokerInfo;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.ValueType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.IncidentIntent;
import io.zeebe.protocol.record.intent.JobBatchIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.intent.MessageIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.DeploymentRecordValue;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.protocol.record.value.deployment.ResourceType;
import io.zeebe.test.broker.protocol.MsgPackHelper;
import io.zeebe.test.util.MsgPackUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.transport.ClientTransport;
import io.zeebe.transport.SocketAddress;
import io.zeebe.transport.Transports;
import io.zeebe.util.buffer.BufferUtil;
import io.zeebe.util.sched.ActorScheduler;
import io.zeebe.util.sched.clock.ControlledActorClock;
import java.io.ByteArrayOutputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.junit.rules.ExternalResource;

public class CommandApiRule extends ExternalResource {

  public static final long DEFAULT_LOCK_DURATION = 10000L;
  private static final String DEFAULT_WORKER = "defaultWorker";

  protected final int nodeId;
  protected final Supplier<AtomixCluster> atomixSupplier;
  protected final int partitionCount;
  private final Int2ObjectHashMap<PartitionTestClient> testPartitionClients =
      new Int2ObjectHashMap<>();
  private final ControlledActorClock controlledActorClock = new ControlledActorClock();
  protected int nextPartitionId = 0;
  protected MsgPackHelper msgPackHelper;
  protected ClientTransport transport;
  protected int defaultPartitionId = -1;
  private AtomixCluster atomix;
  private ActorScheduler scheduler;

  public CommandApiRule(final Supplier<AtomixCluster> atomixSupplier) {
    this(0, 1, atomixSupplier);
  }

  public CommandApiRule(
      final int nodeId, final int partitionCount, final Supplier<AtomixCluster> atomixSupplier) {
    this.nodeId = nodeId;
    this.partitionCount = partitionCount;
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
            brokerInfo -> {
              final String commandApiAddress = brokerInfo.getCommandApiAddress();
              if (commandApiAddress != null) {
                transport.registerEndpoint(
                    brokerInfo.getNodeId(), SocketAddress.from(commandApiAddress));
              }
            });

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

  /** targets the default partition by default */
  public ExecuteCommandRequestBuilder createCmdRequest() {
    return new ExecuteCommandRequestBuilder(transport.getOutput(), nodeId, msgPackHelper)
        .partitionId(defaultPartitionId);
  }

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
      final int partitionId,
      final String type,
      final long lockDuration,
      final int maxJobsToActivate) {
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
        .put("maxJobsToActivate", maxJobsToActivate)
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
                IntStream.range(
                        START_PARTITION_ID, START_PARTITION_ID + brokerInfo.getPartitionsCount())
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

  /** @return the workflow key */
  public Record<DeploymentRecordValue> deployWorkflow(BpmnModelInstance modelInstance) {
    final ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    Bpmn.writeModelToStream(outStream, modelInstance);
    final byte[] resource = outStream.toByteArray();

    final Map<String, Object> deploymentResource = new HashMap<>();
    deploymentResource.put("resource", resource);
    deploymentResource.put("resourceType", ResourceType.BPMN_XML.name());
    deploymentResource.put("resourceName", "testProcess.bpmn");

    final ExecuteCommandResponse commandResponse =
        createCmdRequest()
            .partitionId(Protocol.DEPLOYMENT_PARTITION)
            .type(ValueType.DEPLOYMENT, DeploymentIntent.CREATE)
            .command()
            .put(DeploymentRecord.RESOURCES, Collections.singletonList(deploymentResource))
            .done()
            .sendAndAwait();

    assertThat(commandResponse.getIntent()).isEqualTo(DeploymentIntent.CREATED);

    return RecordingExporter.deploymentRecords(DeploymentIntent.DISTRIBUTED)
        .withRecordKey(commandResponse.getKey())
        .getFirst();
  }

  public void publishMessage(String messageName, String correlationKey) {
    publishMessage(messageName, correlationKey, MsgPackUtil.asMsgPack("{}"));
  }

  public void publishMessage(String messageName, String correlationKey, DirectBuffer variables) {
    publishMessage(messageName, correlationKey, variables, Duration.ofHours(1).toMillis());
  }

  public void publishMessage(
      String messageName, String correlationKey, DirectBuffer variables, long ttl) {
    final ExecuteCommandResponse response =
        createCmdRequest()
            .partitionId(partitionForCorrelationKey(correlationKey))
            .type(ValueType.MESSAGE, MessageIntent.PUBLISH)
            .command()
            .put("name", messageName)
            .put("correlationKey", correlationKey)
            .put("timeToLive", ttl)
            .put("variables", BufferUtil.bufferAsArray(variables))
            .done()
            .sendAndAwait();

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(MessageIntent.PUBLISHED);
  }

  public void cancelWorkflowInstance(long workflowInstanceKey) {
    final ExecuteCommandResponse response =
        createCmdRequest()
            .partitionId(Protocol.decodePartitionId(workflowInstanceKey))
            .type(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceIntent.CANCEL)
            .key(workflowInstanceKey)
            .command()
            .done()
            .sendAndAwait();

    assertThat(response.getRecordType()).isEqualTo(RecordType.EVENT);
    assertThat(response.getIntent()).isEqualTo(WorkflowInstanceIntent.ELEMENT_TERMINATING);
  }

  public void completeJob(String jobType) {
    completeJob(jobType, MsgPackUtil.asMsgPack("{}"));
  }

  public void completeJob(String jobType, DirectBuffer variables) {
    activateJobs(jobType).await();

    final Record<JobRecordValue> jobRecord =
        RecordingExporter.jobRecords(JobIntent.ACTIVATED).withType(jobType).getFirst();

    final ExecuteCommandResponse response =
        createCmdRequest()
            .type(ValueType.JOB, JobIntent.COMPLETE)
            .key(jobRecord.getKey())
            .command()
            .put("variables", BufferUtil.bufferAsArray(variables))
            .done()
            .sendAndAwait();

    assertThat(response.getIntent()).isEqualTo(JobIntent.COMPLETED);
  }

  public ExecuteCommandResponse resolveIncident(long incidentKey) {
    return createCmdRequest()
        .partitionId(Protocol.decodePartitionId(incidentKey))
        .type(ValueType.INCIDENT, IncidentIntent.RESOLVE)
        .key(incidentKey)
        .command()
        .done()
        .sendAndAwait();
  }

  protected int nextPartitionId() {
    return (nextPartitionId++ % partitionCount) + START_PARTITION_ID;
  }

  protected int partitionForCorrelationKey(String correlationKey) {
    return SubscriptionUtil.getSubscriptionPartitionId(
        BufferUtil.wrapString(correlationKey), partitionCount);
  }
}
