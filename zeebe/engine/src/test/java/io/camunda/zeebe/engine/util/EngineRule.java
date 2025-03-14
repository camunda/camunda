/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.util.TestInterPartitionCommandSender.CommandInterceptor;
import io.camunda.zeebe.engine.util.client.AuthorizationClient;
import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.engine.util.client.DecisionEvaluationClient;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.engine.util.client.IncidentClient;
import io.camunda.zeebe.engine.util.client.JobActivationClient;
import io.camunda.zeebe.engine.util.client.JobClient;
import io.camunda.zeebe.engine.util.client.MessageCorrelationClient;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.engine.util.client.ResourceDeletionClient;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.engine.util.client.UserClient;
import io.camunda.zeebe.engine.util.client.UserTaskClient;
import io.camunda.zeebe.engine.util.client.VariableClient;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
import org.junit.rules.ExternalResource;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

public final class EngineRule extends ExternalResource {

  private static final int PARTITION_ID = Protocol.DEPLOYMENT_PARTITION;
  private final StreamProcessorRule environmentRule;
  private final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();
  private final int partitionCount;

  private Consumer<TypedRecord> onProcessedCallback = record -> {};
  private Consumer<LoggedEvent> onSkippedCallback = record -> {};

  private long lastProcessedPosition = -1L;
  private JobStreamer jobStreamer = JobStreamer.noop();

  private FeatureFlags featureFlags = FeatureFlags.createDefaultForTests();
  private ArrayList<TestInterPartitionCommandSender> interPartitionCommandSenders;
  private Consumer<EngineConfiguration> engineConfigModifier = cfg -> {};

  private EngineRule(final int partitionCount) {
    this(partitionCount, null);
  }

  private EngineRule(final int partitionCount, final ListLogStorage sharedStorage) {
    this.partitionCount = partitionCount;
    environmentRule =
        new StreamProcessorRule(
            PARTITION_ID, partitionCount, DefaultZeebeDbFactory.defaultFactory(), sharedStorage);
  }

  public static EngineRule singlePartition() {
    return new EngineRule(1);
  }

  public static EngineRule multiplePartition(final int partitionCount) {
    return new EngineRule(partitionCount);
  }

  public static EngineRule withSharedStorage(final ListLogStorage listLogStorage) {
    return new EngineRule(1, listLogStorage);
  }

  @Override
  public Statement apply(final Statement base, final Description description) {
    Statement statement = recordingExporterTestWatcher.apply(base, description);
    statement = super.apply(statement, description);
    return environmentRule.apply(statement, description);
  }

  @Override
  protected void before() {
    start();
  }

  public void start() {
    start(StreamProcessorMode.PROCESSING, true);
  }

  public void start(final StreamProcessorMode mode, final boolean awaitOpening) {
    startProcessors(mode, awaitOpening);
  }

  public void stop() {
    forEachPartition(environmentRule::closeStreamProcessor);
  }

  public EngineRule withJobStreamer(final JobStreamer jobStreamer) {
    this.jobStreamer = jobStreamer;
    return this;
  }

  public EngineRule withFeatureFlags(final FeatureFlags featureFlags) {
    this.featureFlags = featureFlags;
    return this;
  }

  public EngineRule withOnProcessedCallback(final Consumer<TypedRecord> onProcessedCallback) {
    this.onProcessedCallback = this.onProcessedCallback.andThen(onProcessedCallback);
    return this;
  }

  public EngineRule withOnSkippedCallback(final Consumer<LoggedEvent> onSkippedCallback) {
    this.onSkippedCallback = this.onSkippedCallback.andThen(onSkippedCallback);
    return this;
  }

  public EngineRule withStreamProcessorMode(final StreamProcessorMode streamProcessorMode) {
    environmentRule.withStreamProcessorMode(streamProcessorMode);
    return this;
  }

  public EngineRule withEngineConfig(final Consumer<EngineConfiguration> modifier) {
    engineConfigModifier = engineConfigModifier.andThen(modifier);
    return this;
  }

  private void startProcessors(final StreamProcessorMode mode, final boolean awaitOpening) {
    interPartitionCommandSenders = new ArrayList<>();

    forEachPartition(
        partitionId -> {
          final var interPartitionCommandSender =
              new TestInterPartitionCommandSender(environmentRule::newLogStreamWriter);
          interPartitionCommandSenders.add(interPartitionCommandSender);
          environmentRule.startTypedStreamProcessor(
              partitionId,
              (recordProcessorContext) -> {
                engineConfigModifier.accept(recordProcessorContext.getConfig());
                return EngineProcessors.createEngineProcessors(
                        recordProcessorContext,
                        partitionCount,
                        new SubscriptionCommandSender(partitionId, interPartitionCommandSender),
                        interPartitionCommandSender,
                        featureFlags,
                        jobStreamer)
                    .withListener(
                        new ProcessingExporterTransistor(
                            environmentRule.getLogStream(partitionId)));
              },
              Optional.of(
                  new StreamProcessorListener() {
                    @Override
                    public void onProcessed(final TypedRecord<?> processedCommand) {
                      lastProcessedPosition = processedCommand.getPosition();
                      onProcessedCallback.accept(processedCommand);
                    }

                    @Override
                    public void onSkipped(final LoggedEvent skippedRecord) {
                      lastProcessedPosition = skippedRecord.getPosition();
                      onSkippedCallback.accept(skippedRecord);
                    }
                  }),
              cfg -> cfg.streamProcessorMode(mode),
              awaitOpening);
        });
    interPartitionCommandSenders.forEach(s -> s.initializeWriters(partitionCount));
  }

  public void snapshot() {
    environmentRule.snapshot();
  }

  public void forEachPartition(final Consumer<Integer> partitionIdConsumer) {
    int partitionId = PARTITION_ID;
    for (int i = 0; i < partitionCount; i++) {
      partitionIdConsumer.accept(partitionId++);
    }
  }

  public void increaseTime(final Duration duration) {
    final var streamProcessor = environmentRule.getStreamProcessor(PARTITION_ID);
    if (streamProcessor.getCurrentPhase().join() == Phase.PROCESSING) {
      // When time traveling, we're generally want to make sure that the entire state machine cycle
      // for processing a record is completed, including the execution of post-commit tasks. For
      // example, we're often interested in scheduled timers when time traveling in tests, for which
      // the due date checker is scheduled through a post-commit task. When the engine has reached
      // the end of the log, all post-commit tasks have also been applied, because the state machine
      // will have executed them before switching the hasReachEnd flag.
      Awaitility.await("Expect that engine reaches the end of the log before increasing the time")
          .until(this::hasReachedEnd);
    }

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

    startProcessors(StreamProcessorMode.PROCESSING, true);
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

  public ProcessingState getProcessingState() {
    return environmentRule.getProcessingState();
  }

  public ProcessingState getProcessingState(final int partitionId) {
    return environmentRule.getProcessingState(partitionId);
  }

  public StreamProcessor getStreamProcessor(final int partitionId) {
    return environmentRule.getStreamProcessor(partitionId);
  }

  public StreamClock getStreamClock() {
    return getStreamClock(PARTITION_ID);
  }

  public StreamClock getStreamClock(final int partitionId) {
    return environmentRule.getStreamClock(partitionId);
  }

  public MeterRegistry getMeterRegistry() {
    return getMeterRegistry(PARTITION_ID);
  }

  public MeterRegistry getMeterRegistry(final int partitionId) {
    return environmentRule.getMeterRegistry(partitionId);
  }

  public long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public DeploymentClient deployment() {
    return new DeploymentClient(environmentRule, this::forEachPartition, partitionCount);
  }

  public ProcessInstanceClient processInstance() {
    return new ProcessInstanceClient(environmentRule);
  }

  public DecisionEvaluationClient decision() {
    return new DecisionEvaluationClient(environmentRule);
  }

  public PublishMessageClient message() {
    return new PublishMessageClient(environmentRule, partitionCount);
  }

  public MessageCorrelationClient messageCorrelation() {
    return new MessageCorrelationClient(environmentRule, partitionCount);
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

  public ResourceDeletionClient resourceDeletion() {
    return new ResourceDeletionClient(environmentRule);
  }

  public SignalClient signal() {
    return new SignalClient(environmentRule);
  }

  public UserTaskClient userTask() {
    return new UserTaskClient(environmentRule);
  }

  public UserClient user() {
    return new UserClient(environmentRule);
  }

  public AuthorizationClient authorization() {
    return new AuthorizationClient(environmentRule);
  }

  public Record<JobRecordValue> createJob(final String type, final String processId) {
    return createJob(type, processId, Collections.emptyMap());
  }

  public Record<JobRecordValue> createJob(
      final String type, final String processId, final Map<String, Object> variables) {
    return createJob(type, processId, variables, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  public Record<JobRecordValue> createJob(
      final String type,
      final String processId,
      final Map<String, Object> variables,
      final String tenantId) {
    deployment()
        .withXmlResource(
            processId + ".bpmn",
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(type).done())
                .endEvent("end")
                .done())
        .withTenantId(tenantId)
        .deploy();

    final long instanceKey =
        processInstance()
            .ofBpmnProcessId(processId)
            .withVariables(variables)
            .withTenantId(tenantId)
            .create();

    return jobRecords(JobIntent.CREATED)
        .withType(type)
        .withTenantId(tenantId)
        .filter(r -> r.getValue().getProcessInstanceKey() == instanceKey)
        .getFirst();
  }

  public void writeRecords(final RecordToWrite... records) {
    environmentRule.writeBatch(records);
  }

  public CommandResponseWriter getCommandResponseWriter() {
    return environmentRule.getCommandResponseWriter();
  }

  public void pauseProcessing(final int partitionId) {
    environmentRule.pauseProcessing(partitionId);
  }

  public void banInstanceInNewTransaction(final int partitionId, final long processInstanceKey) {
    environmentRule.banInstanceInNewTransaction(partitionId, processInstanceKey);
  }

  public void resumeProcessing(final int partitionId) {
    environmentRule.resumeProcessing(partitionId);
  }

  public Map<ZbColumnFamilies, Map<Object, Object>> collectState() {

    final var keyInstance = new VersatileBlob();
    final var valueInstance = new VersatileBlob();

    return Arrays.stream(ZbColumnFamilies.values())
        .collect(
            Collectors.toMap(
                Function.identity(),
                columnFamily -> {
                  final var entries = new HashMap<>();
                  ((ProcessingDbState) getProcessingState())
                      .forEach(
                          columnFamily,
                          keyInstance,
                          valueInstance,
                          (key, value) ->
                              entries.put(
                                  Arrays.toString(
                                      BufferUtil.cloneBuffer(key.getDirectBuffer())
                                          .byteArray()), // the key is written as plain bytes
                                  MsgPackConverter.convertToJson(value.getDirectBuffer())));
                  return entries;
                }));
  }

  public void awaitProcessingOf(final Record<?> record) {
    final var recordPosition = record.getPosition();

    Awaitility.await(
            String.format(
                "Await the %s.%s to be processed at position %d",
                record.getValueType(), record.getIntent(), recordPosition))
        .untilAsserted(
            () ->
                assertThat(getLastProcessedPosition())
                    .describedAs(
                        "Last process position should be greater or equal to " + recordPosition)
                    .isGreaterThanOrEqualTo(recordPosition));
  }

  public boolean hasReachedEnd() {
    return getStreamProcessor(PARTITION_ID).hasProcessingReachedTheEnd().join();
  }

  public EngineRule maxCommandsInBatch(final int maxCommandsInBatch) {
    environmentRule.maxCommandsInBatch(maxCommandsInBatch);
    return this;
  }

  public void interceptInterPartitionCommands(final CommandInterceptor interceptor) {
    if (interPartitionCommandSenders == null) {
      throw new IllegalStateException(
          "Cannot intercept inter-partition commands before the engine is started");
    }
    interPartitionCommandSenders.forEach(sender -> sender.intercept(interceptor));
  }

  public ClockClient clock() {
    return new ClockClient(environmentRule);
  }

  private static final class VersatileBlob implements DbKey, DbValue {

    private final DirectBuffer genericBuffer = new UnsafeBuffer(0, 0);

    @Override
    public void wrap(final DirectBuffer buffer, final int offset, final int length) {
      genericBuffer.wrap(buffer, offset, length);
    }

    @Override
    public int getLength() {
      return genericBuffer.capacity();
    }

    @Override
    public void write(final MutableDirectBuffer buffer, final int offset) {
      buffer.putBytes(offset, genericBuffer, 0, genericBuffer.capacity());
    }

    public DirectBuffer getDirectBuffer() {
      return genericBuffer;
    }
  }
}
