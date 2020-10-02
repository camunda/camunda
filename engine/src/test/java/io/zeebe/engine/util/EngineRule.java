/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.util;

import static io.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.processor.ReadonlyProcessingContext;
import io.zeebe.engine.processor.RecordValues;
import io.zeebe.engine.processor.StreamProcessor;
import io.zeebe.engine.processor.StreamProcessorLifecycleAware;
import io.zeebe.engine.processor.TypedEventImpl;
import io.zeebe.engine.processor.workflow.EngineProcessors;
import io.zeebe.engine.processor.workflow.deployment.distribute.DeploymentDistributor;
import io.zeebe.engine.processor.workflow.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.processor.workflow.message.command.PartitionCommandSender;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandMessageHandler;
import io.zeebe.engine.processor.workflow.message.command.SubscriptionCommandSender;
import io.zeebe.engine.state.DefaultZeebeDbFactory;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.util.client.DeploymentClient;
import io.zeebe.engine.util.client.IncidentClient;
import io.zeebe.engine.util.client.JobActivationClient;
import io.zeebe.engine.util.client.JobClient;
import io.zeebe.engine.util.client.PublishMessageClient;
import io.zeebe.engine.util.client.VariableClient;
import io.zeebe.engine.util.client.WorkflowInstanceClient;
import io.zeebe.logstreams.log.LogStream;
import io.zeebe.logstreams.log.LogStreamReader;
import io.zeebe.logstreams.log.LoggedEvent;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import io.zeebe.protocol.record.intent.JobIntent;
import io.zeebe.protocol.record.value.JobRecordValue;
import io.zeebe.test.util.TestUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.zeebe.util.buffer.BufferWriter;
import io.zeebe.util.sched.ActorCondition;
import io.zeebe.util.sched.ActorControl;
import io.zeebe.util.sched.clock.ControlledActorClock;
import io.zeebe.util.sched.future.ActorFuture;
import io.zeebe.util.sched.future.CompletableActorFuture;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class EngineRule extends ExternalResource {

  private static final int PARTITION_ID = Protocol.DEPLOYMENT_PARTITION;
  private static final RecordingExporter RECORDING_EXPORTER = new RecordingExporter();
  private final StreamProcessorRule environmentRule;
  private final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private final int partitionCount;
  private final boolean explicitStart;
  private Consumer<String> jobsAvailableCallback = type -> {};

  private final Int2ObjectHashMap<SubscriptionCommandMessageHandler> subscriptionHandlers =
      new Int2ObjectHashMap<>();
  private ExecutorService subscriptionHandlerExecutor;

  private EngineRule(final int partitionCount) {
    this(partitionCount, false);
  }

  private EngineRule(final int partitionCount, final boolean explicitStart) {
    this.partitionCount = partitionCount;
    this.explicitStart = explicitStart;
    environmentRule =
        new StreamProcessorRule(
            PARTITION_ID, partitionCount, DefaultZeebeDbFactory.DEFAULT_DB_FACTORY);
  }

  public static EngineRule singlePartition() {
    return new EngineRule(1);
  }

  public static EngineRule multiplePartition(final int partitionCount) {
    return new EngineRule(partitionCount);
  }

  public static EngineRule explicitStart() {
    return new EngineRule(1, true);
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    Statement statement = recordingExporterTestWatcher.apply(base, description);
    statement = super.apply(statement, description);
    return environmentRule.apply(statement, description);
  }

  @Override
  protected void before() {
    subscriptionHandlerExecutor = Executors.newSingleThreadExecutor();

    if (!explicitStart) {
      startProcessors();
    }
  }

  @Override
  protected void after() {
    subscriptionHandlerExecutor.shutdown();
    subscriptionHandlers.clear();
  }

  public void start() {
    startProcessors();
  }

  public void stop() {
    forEachPartition(environmentRule::closeStreamProcessor);
  }

  public EngineRule withJobsAvailableCallback(final Consumer<String> callback) {
    jobsAvailableCallback = callback;
    return this;
  }

  private void startProcessors() {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final UnsafeBuffer deploymentBuffer = new UnsafeBuffer(new byte[deploymentRecord.getLength()]);
    deploymentRecord.write(deploymentBuffer, 0);

    final PendingDeploymentDistribution deploymentDistribution =
        mock(PendingDeploymentDistribution.class);
    when(deploymentDistribution.getDeployment()).thenReturn(deploymentBuffer);

    forEachPartition(
        partitionId -> {
          environmentRule.startTypedStreamProcessor(
              partitionId,
              (processingContext) ->
                  EngineProcessors.createEngineProcessors(
                          processingContext,
                          partitionCount,
                          new SubscriptionCommandSender(
                              partitionId, new PartitionCommandSenderImpl()),
                          new DeploymentDistributionImpl(),
                          (key, partition) -> {},
                          jobsAvailableCallback)
                      .withListener(new ProcessingExporterTransistor()));

          // sequenialize the commands to avoid concurrency
          subscriptionHandlers.put(
              partitionId,
              new SubscriptionCommandMessageHandler(
                  subscriptionHandlerExecutor::submit, environmentRule::getLogStreamRecordWriter));
        });
  }

  public void forEachPartition(final Consumer<Integer> partitionIdConsumer) {
    int partitionId = PARTITION_ID;
    for (int i = 0; i < partitionCount; i++) {
      partitionIdConsumer.accept(partitionId++);
    }
  }

  public void increaseTime(final Duration duration) {
    environmentRule.getClock().addTime(duration);
  }

  public void reprocess() {
    forEachPartition(
        partitionId -> {
          try {
            environmentRule.closeStreamProcessor(partitionId);
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });

    final int lastSize = RecordingExporter.getRecords().size();
    // we need to reset the record exporter
    RecordingExporter.reset();

    startProcessors();
    TestUtil.waitUntil(
        () -> RecordingExporter.getRecords().size() >= lastSize,
        "Failed to reprocess all events, only re-exported %d but expected %d",
        RecordingExporter.getRecords().size(),
        lastSize);
  }

  public List<Integer> getPartitionIds() {
    return IntStream.range(PARTITION_ID, PARTITION_ID + partitionCount)
        .boxed()
        .collect(Collectors.toList());
  }

  public ControlledActorClock getClock() {
    return environmentRule.getClock();
  }

  public ZeebeState getZeebeState() {
    return environmentRule.getZeebeState();
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return environmentRule.getStreamProcessor(partitionId);
  }

  public DeploymentClient deployment() {
    return new DeploymentClient(environmentRule, this::forEachPartition);
  }

  public WorkflowInstanceClient workflowInstance() {
    return new WorkflowInstanceClient(environmentRule);
  }

  public PublishMessageClient message() {
    return new PublishMessageClient(environmentRule, partitionCount);
  }

  public VariableClient variables() {
    return new VariableClient(environmentRule);
  }

  public JobActivationClient jobs() {
    return new JobActivationClient(environmentRule);
  }

  public JobClient job() {
    return new JobClient(environmentRule);
  }

  public IncidentClient incident() {
    return new IncidentClient(environmentRule);
  }

  public Record<JobRecordValue> createJob(final String type, final String processId) {
    deployment()
        .withXmlResource(
            processId,
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(type).done())
                .endEvent("end")
                .done())
        .deploy();

    final long instanceKey = workflowInstance().ofBpmnProcessId(processId).create();

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .filter(r -> r.getValue().getWorkflowInstanceKey() == instanceKey)
        .getFirst();
  }

  public void writeRecords(final RecordToWrite... records) {
    environmentRule.writeBatch(records);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return environmentRule.getCommandResponseWriter();
  }

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////// PROCESSOR EXPORTER CROSSOVER ///////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  private static class ProcessingExporterTransistor implements StreamProcessorLifecycleAware {

    private final RecordValues recordValues = new RecordValues();
    private final RecordMetadata metadata = new RecordMetadata();

    private LogStreamReader logStreamReader;
    private TypedEventImpl typedEvent;

    @Override
    public void onRecovered(final ReadonlyProcessingContext context) {
      final int partitionId = context.getLogStream().getPartitionId();
      typedEvent = new TypedEventImpl(partitionId);
      final ActorControl actor = context.getActor();

      final ActorCondition onCommitCondition =
          actor.onCondition("on-commit", this::onNewEventCommitted);
      final LogStream logStream = context.getLogStream();
      logStream.registerOnCommitPositionUpdatedCondition(onCommitCondition);
      logStream
          .newLogStreamReader()
          .onComplete(
              ((reader, throwable) -> {
                if (throwable == null) {
                  logStreamReader = reader;
                  onNewEventCommitted();
                }
              }));
    }

    private void onNewEventCommitted() {
      if (logStreamReader == null) {
        return;
      }

      while (logStreamReader.hasNext()) {
        final LoggedEvent rawEvent = logStreamReader.next();
        metadata.reset();
        rawEvent.readMetadata(metadata);

        final UnifiedRecordValue recordValue =
            recordValues.readRecordValue(rawEvent, metadata.getValueType());
        typedEvent.wrap(rawEvent, metadata, recordValue);

        RECORDING_EXPORTER.export(typedEvent);
      }
    }
  }

  private final class DeploymentDistributionImpl implements DeploymentDistributor {

    private final Map<Long, PendingDeploymentDistribution> pendingDeployments = new HashMap<>();

    @Override
    public ActorFuture<Void> pushDeployment(
        final long key, final long position, final DirectBuffer buffer) {
      final PendingDeploymentDistribution pendingDeployment =
          new PendingDeploymentDistribution(buffer, position, partitionCount);
      pendingDeployments.put(key, pendingDeployment);

      forEachPartition(
          partitionId -> {
            if (partitionId == PARTITION_ID) {
              return;
            }

            final DeploymentRecord deploymentRecord = new DeploymentRecord();
            deploymentRecord.wrap(buffer);

            // we run in processor actor, we are not allowed to wait on futures
            // which means we cant get new writer in sync way
            new Thread(
                    () ->
                        environmentRule.writeCommandOnPartition(
                            partitionId, key, DeploymentIntent.CREATE, deploymentRecord))
                .start();
          });

      return CompletableActorFuture.completed(null);
    }

    @Override
    public PendingDeploymentDistribution removePendingDeployment(final long key) {
      return pendingDeployments.remove(key);
    }
  }

  private class PartitionCommandSenderImpl implements PartitionCommandSender {

    @Override
    public boolean sendCommand(final int receiverPartitionId, final BufferWriter command) {

      final byte[] bytes = new byte[command.getLength()];
      final UnsafeBuffer commandBuffer = new UnsafeBuffer(bytes);
      command.write(commandBuffer, 0);

      // delegate the command to the subscription handler of the receiver partition
      subscriptionHandlers.get(receiverPartitionId).apply(bytes);

      return true;
    }
  }
}
