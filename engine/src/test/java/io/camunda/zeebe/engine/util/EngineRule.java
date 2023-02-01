/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.util;

import static io.camunda.zeebe.test.util.record.RecordingExporter.jobRecords;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.deployment.distribute.DeploymentDistributionCommandSender;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ZeebeDbState;
import io.camunda.zeebe.engine.state.immutable.ZeebeState;
import io.camunda.zeebe.engine.util.client.DecisionEvaluationClient;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.engine.util.client.IncidentClient;
import io.camunda.zeebe.engine.util.client.JobActivationClient;
import io.camunda.zeebe.engine.util.client.JobClient;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.engine.util.client.ResourceDeletionClient;
import io.camunda.zeebe.engine.util.client.VariableClient;
import io.camunda.zeebe.logstreams.log.LogAppendEntry;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.logstreams.log.LoggedEvent;
import io.camunda.zeebe.logstreams.util.ListLogStorage;
import io.camunda.zeebe.logstreams.util.SynchronousLogStream;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.InterPartitionCommandSender;
import io.camunda.zeebe.stream.api.ReadonlyStreamProcessorContext;
import io.camunda.zeebe.stream.api.StreamProcessorLifecycleAware;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.stream.impl.records.RecordValues;
import io.camunda.zeebe.stream.impl.records.TypedRecordImpl;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.awaitility.Awaitility;
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

  private Consumer<String> jobsAvailableCallback = type -> {};
  private Consumer<TypedRecord> onProcessedCallback = record -> {};
  private Consumer<LoggedEvent> onSkippedCallback = record -> {};

  private final Map<Integer, ReprocessingCompletedListener> partitionReprocessingCompleteListeners =
      new Int2ObjectHashMap<>();

  private long lastProcessedPosition = -1L;

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
    startProcessors();
  }

  @Override
  protected void after() {
    partitionReprocessingCompleteListeners.clear();
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

  private void startProcessors() {
    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    final UnsafeBuffer deploymentBuffer = new UnsafeBuffer(new byte[deploymentRecord.getLength()]);
    deploymentRecord.write(deploymentBuffer, 0);
    final var interPartitionCommandSenders = new ArrayList<TestInterPartitionCommandSender>();

    forEachPartition(
        partitionId -> {
          final var reprocessingCompletedListener = new ReprocessingCompletedListener();
          partitionReprocessingCompleteListeners.put(partitionId, reprocessingCompletedListener);
          final var featureFlags = FeatureFlags.createDefaultForTests();

          final var interPartitionCommandSender = new TestInterPartitionCommandSender();
          interPartitionCommandSenders.add(interPartitionCommandSender);
          environmentRule.startTypedStreamProcessor(
              partitionId,
              (recordProcessorContext) ->
                  EngineProcessors.createEngineProcessors(
                          recordProcessorContext,
                          partitionCount,
                          new SubscriptionCommandSender(partitionId, interPartitionCommandSender),
                          new DeploymentDistributionCommandSender(
                              partitionId, interPartitionCommandSender),
                          jobsAvailableCallback,
                          featureFlags)
                      .withListener(
                          new ProcessingExporterTransistor(
                              environmentRule.getLogStream(partitionId)))
                      .withListener(reprocessingCompletedListener),
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
                  }));
        });
    interPartitionCommandSenders.forEach(s -> s.initializeWriters(partitionCount));
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

  public long getLastProcessedPosition() {
    return lastProcessedPosition;
  }

  public DeploymentClient deployment() {
    return new DeploymentClient(environmentRule, this::forEachPartition);
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

  public Record<JobRecordValue> createJob(final String type, final String processId) {
    deployment()
        .withXmlResource(
            processId + ".bpmn",
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(type).done())
                .endEvent("end")
                .done())
        .deploy();

    final long instanceKey = processInstance().ofBpmnProcessId(processId).create();

    return jobRecords(JobIntent.CREATED)
        .withType(type)
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
                  ((ZeebeDbState) getZeebeState())
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

  /////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////// PROCESSOR EXPORTER CROSSOVER ///////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////////////////////////

  private static class ProcessingExporterTransistor implements StreamProcessorLifecycleAware {

    private final RecordValues recordValues = new RecordValues();
    private final RecordMetadata metadata = new RecordMetadata();

    private LogStreamReader logStreamReader;
    private TypedRecordImpl typedEvent;
    private final SynchronousLogStream synchronousLogStream;
    private final ExecutorService executorService;

    public ProcessingExporterTransistor(final SynchronousLogStream synchronousLogStream) {
      this.synchronousLogStream = synchronousLogStream;
      executorService = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onRecovered(final ReadonlyStreamProcessorContext context) {
      executorService.submit(
          () -> {
            final int partitionId = context.getPartitionId();
            typedEvent = new TypedRecordImpl(partitionId);
            final var asyncLogStream = synchronousLogStream.getAsyncLogStream();
            asyncLogStream.registerRecordAvailableListener(this::onNewEventCommitted);
            logStreamReader = asyncLogStream.newLogStreamReader().join();
            exportEvents();
          });
    }

    @Override
    public void onClose() {
      executorService.shutdownNow();
    }

    private void onNewEventCommitted() {
      // this is called from outside (LogStream), so we need to enqueue the task
      if (executorService.isShutdown()) {
        return;
      }

      executorService.submit(this::exportEvents);
    }

    private void exportEvents() {
      // we need to skip until onRecovered happened
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

  private final class ReprocessingCompletedListener implements StreamProcessorLifecycleAware {
    private final ActorFuture<Void> reprocessingComplete = new CompletableActorFuture<>();

    @Override
    public void onRecovered(final ReadonlyStreamProcessorContext context) {
      reprocessingComplete.complete(null);
    }
  }

  private class TestInterPartitionCommandSender implements InterPartitionCommandSender {

    private final Map<Integer, LogStreamWriter> writers = new HashMap<>();

    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final UnifiedRecordValue command) {
      sendCommand(receiverPartitionId, valueType, intent, null, command);
    }

    @Override
    public void sendCommand(
        final int receiverPartitionId,
        final ValueType valueType,
        final Intent intent,
        final Long recordKey,
        final UnifiedRecordValue command) {
      final var metadata =
          new RecordMetadata().recordType(RecordType.COMMAND).intent(intent).valueType(valueType);
      final var writer = writers.get(receiverPartitionId);
      final LogAppendEntry entry;
      if (recordKey != null) {
        entry = LogAppendEntry.of(recordKey, metadata, command);
      } else {
        entry = LogAppendEntry.of(metadata, command);
      }

      writer.tryWrite(entry);
    }

    // Pre-initialize dedicated writers.
    // We must build new writers because reusing the writers from the environmentRule is unsafe.
    // We can't build them on-demand during `sendCommand` because that might run within an actor
    // context where we can't build new `SyncLogStream`s.
    private void initializeWriters(final int partitionCount) {
      for (int i = PARTITION_ID; i < PARTITION_ID + partitionCount; i++) {
        writers.put(i, environmentRule.newLogStreamWriter(i));
      }
    }
  }
}
