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

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.EngineProcessors;
import io.camunda.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.camunda.zeebe.engine.processing.streamprocessor.JobStreamer;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.engine.state.immutable.RoutingState;
import io.camunda.zeebe.engine.state.migration.DbMigratorImpl;
import io.camunda.zeebe.engine.util.TestInterPartitionCommandSender.CommandInterceptor;
import io.camunda.zeebe.engine.util.client.AdHocSubProcessActivityClient;
import io.camunda.zeebe.engine.util.client.AuthorizationClient;
import io.camunda.zeebe.engine.util.client.BatchOperationClient;
import io.camunda.zeebe.engine.util.client.ClockClient;
import io.camunda.zeebe.engine.util.client.DecisionEvaluationClient;
import io.camunda.zeebe.engine.util.client.DeploymentClient;
import io.camunda.zeebe.engine.util.client.GroupClient;
import io.camunda.zeebe.engine.util.client.IdentitySetupClient;
import io.camunda.zeebe.engine.util.client.IncidentClient;
import io.camunda.zeebe.engine.util.client.JobActivationClient;
import io.camunda.zeebe.engine.util.client.JobClient;
import io.camunda.zeebe.engine.util.client.MappingRuleClient;
import io.camunda.zeebe.engine.util.client.MessageCorrelationClient;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient;
import io.camunda.zeebe.engine.util.client.PublishMessageClient;
import io.camunda.zeebe.engine.util.client.ResourceDeletionClient;
import io.camunda.zeebe.engine.util.client.ResourceFetchClient;
import io.camunda.zeebe.engine.util.client.RoleClient;
import io.camunda.zeebe.engine.util.client.ScaleClient;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.engine.util.client.TenantClient;
import io.camunda.zeebe.engine.util.client.UsageMetricClient;
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
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.scheduler.ActorScheduler;
import io.camunda.zeebe.scheduler.clock.ControlledActorClock;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.stream.impl.ClusterContextImpl;
import io.camunda.zeebe.stream.impl.StreamProcessor;
import io.camunda.zeebe.stream.impl.StreamProcessor.Phase;
import io.camunda.zeebe.stream.impl.StreamProcessorListener;
import io.camunda.zeebe.stream.impl.StreamProcessorMode;
import io.camunda.zeebe.test.util.TestUtil;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher.ResetMode;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.buffer.BufferUtil;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
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
  private ResetRecordingExporterTestWatcherMode resetRecordingExporterTestWatcherMode =
      ResetRecordingExporterTestWatcherMode.BEFORE_EACH_TEST;
  private final int partitionCount;
  private boolean awaitIdentitySetup = false;
  private ResetRecordingExporterMode awaitIdentitySetupResetMode =
      ResetRecordingExporterMode.AFTER_IDENTITY_SETUP;
  private boolean initializeRoutingState = true;

  private Consumer<TypedRecord> onProcessedCallback = record -> {};
  private Consumer<LoggedEvent> onSkippedCallback = record -> {};

  private long lastProcessedPosition = -1L;
  private JobStreamer jobStreamer = JobStreamer.noop();

  private final FeatureFlags featureFlags = FeatureFlags.createDefaultForTests();
  private ArrayList<TestInterPartitionCommandSender> interPartitionCommandSenders;
  private Consumer<SecurityConfiguration> securityConfigModifier =
      cfg -> cfg.getAuthorizations().setEnabled(false);
  private Consumer<EngineConfiguration> engineConfigModifier = cfg -> {};
  private SearchClientsProxy searchClientsProxy;
  private Optional<RoutingState> initialRoutingState = Optional.empty();

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
    if (EnumSet.of(
            ResetRecordingExporterTestWatcherMode.ONLY_BEFORE_AND_AFTER_ALL_TESTS,
            ResetRecordingExporterTestWatcherMode.BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST)
        .contains(resetRecordingExporterTestWatcherMode)) {
      RecordingExporter.reset();
    }
    start();
    if (awaitIdentitySetup) {
      awaitIdentitySetup();
    }
  }

  @Override
  protected void after() {
    if (resetRecordingExporterTestWatcherMode
        == ResetRecordingExporterTestWatcherMode.ONLY_BEFORE_AND_AFTER_ALL_TESTS) {
      RecordingExporter.reset();
    }
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

  public EngineRule withIdentitySetup() {
    awaitIdentitySetup = true;
    withFeatureFlags(ff -> ff.setEnableIdentitySetup(true));
    return this;
  }

  public EngineRule withIdentitySetup(
      final ResetRecordingExporterMode awaitIdentitySetupResetMode) {
    if (awaitIdentitySetupResetMode == ResetRecordingExporterMode.NO_RESET_AFTER_IDENTITY_SETUP
        && resetRecordingExporterTestWatcherMode
            == ResetRecordingExporterTestWatcherMode.BEFORE_EACH_TEST) {
      throw new IllegalStateException(
          """
          Expected to not reset RecordingExporter after identity setup, \
          but the RecordingExporterTestWatcher is configured to reset before each test. \
          This would mean that the identity setup is still not included in the recording exporter. \
          If you want to include the identity setup in the recording exporter, please call \
          .withResetRecordingExporterTestWatcherMode on the EngineRule to change the reset mode, \
          and choose one of the following modes:
          - ResetRecordingExporterTestWatcherMode.ONLY_BEFORE_AND_AFTER_ALL_TESTS
          - ResetRecordingExporterTestWatcherMode.BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST

          Additionally, ensure that the RecordingExporterTestWatcher is not explicitly set up in the
          test class.
          """);
    }
    this.awaitIdentitySetupResetMode = awaitIdentitySetupResetMode;
    return withIdentitySetup();
  }

  public EngineRule withJobStreamer(final JobStreamer jobStreamer) {
    this.jobStreamer = jobStreamer;
    return this;
  }

  public EngineRule withFeatureFlags(final Consumer<FeatureFlags> modifier) {
    modifier.accept(featureFlags);
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

  public EngineRule withSecurityConfig(final Consumer<SecurityConfiguration> modifier) {
    securityConfigModifier = securityConfigModifier.andThen(modifier);
    return this;
  }

  public EngineRule withEngineConfig(final Consumer<EngineConfiguration> modifier) {
    engineConfigModifier = engineConfigModifier.andThen(modifier);
    return this;
  }

  public EngineRule withSearchClientsProxy(final SearchClientsProxy searchClientsProxy) {
    this.searchClientsProxy = searchClientsProxy;
    return this;
  }

  public EngineRule withInitializeRoutingState(final boolean initializeRoutingState) {
    this.initializeRoutingState = initializeRoutingState;
    return this;
  }

  public EngineRule withInitialRoutingState(final RoutingState routingInfo) {
    initializeRoutingState = false;
    initialRoutingState = Optional.of(routingInfo);
    return this;
  }

  public EngineRule withResetRecordingExporterTestWatcherMode(
      final ResetRecordingExporterTestWatcherMode resetMode) {
    resetRecordingExporterTestWatcherMode = resetMode;
    return switch (resetMode) {
      case ONLY_BEFORE_AND_AFTER_ALL_TESTS -> {
        // so, never on individual tests
        recordingExporterTestWatcher.withResetMode(ResetMode.NEVER);
        yield this;
      }
      case BEFORE_EACH_TEST -> {
        recordingExporterTestWatcher.withResetMode(ResetMode.ON_STARTING);
        yield this;
      }
      case BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST -> {
        recordingExporterTestWatcher.withResetMode(ResetMode.ON_FINISHED);
        yield this;
      }
    };
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
                if (initializeRoutingState) {
                  final DbMigratorImpl migrator =
                      new DbMigratorImpl(
                          false,
                          new ClusterContextImpl(partitionCount),
                          recordProcessorContext.getProcessingState(),
                          null);

                  migrator.runMigrations();
                } else if (initialRoutingState.isPresent()) {
                  final var state = initialRoutingState.get();
                  final var dbRoutingState =
                      recordProcessorContext.getProcessingState().getRoutingState();
                  dbRoutingState.initializeRoutingInfo(state.currentPartitions().size());
                  dbRoutingState.setMessageCorrelation(state.messageCorrelation());
                  dbRoutingState.setDesiredPartitions(state.desiredPartitions(), 0L);
                }

                securityConfigModifier.accept(recordProcessorContext.getSecurityConfig());
                engineConfigModifier.accept(recordProcessorContext.getConfig());
                return EngineProcessors.createEngineProcessors(
                        recordProcessorContext,
                        partitionCount,
                        new SubscriptionCommandSender(partitionId, interPartitionCommandSender),
                        interPartitionCommandSender,
                        featureFlags,
                        jobStreamer,
                        searchClientsProxy)
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

  public ResourceFetchClient resourceFetch() {
    return new ResourceFetchClient(environmentRule);
  }

  public AdHocSubProcessActivityClient adHocSubProcessActivity() {
    return new AdHocSubProcessActivityClient(environmentRule);
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

  public IdentitySetupClient identitySetup() {
    return new IdentitySetupClient(environmentRule);
  }

  public AuthorizationClient authorization() {
    return new AuthorizationClient(environmentRule);
  }

  public RoleClient role() {
    return new RoleClient(environmentRule);
  }

  public TenantClient tenant() {
    return new TenantClient(environmentRule);
  }

  public MappingRuleClient mappingRule() {
    return new MappingRuleClient(environmentRule);
  }

  public GroupClient group() {
    return new GroupClient(environmentRule);
  }

  public BatchOperationClient batchOperation() {
    return new BatchOperationClient(environmentRule);
  }

  public UsageMetricClient usageMetrics() {
    return new UsageMetricClient(environmentRule);
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

  public long writeRecords(final RecordToWrite... records) {
    return environmentRule.writeBatch(records);
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

  public void awaitIdentitySetup() {
    final var totalAmountOfAuthorizationsCreated =
        AuthorizationResourceType.getUserProvidedResourceTypes().size();

    if (partitionCount > 1) {
      RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZED).await();
      RecordingExporter.commandDistributionRecords(CommandDistributionIntent.FINISHED)
          .withDistributionIntent(AuthorizationIntent.CREATE)
          .skip(totalAmountOfAuthorizationsCreated - 1)
          .await();
    } else {
      RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZED).await();
      RecordingExporter.authorizationRecords(AuthorizationIntent.CREATED)
          .skip(totalAmountOfAuthorizationsCreated - 1)
          .await();
    }

    if (awaitIdentitySetupResetMode == ResetRecordingExporterMode.AFTER_IDENTITY_SETUP) {
      // reset the RecordingExporter to avoid that the identity setup is included in the test
      RecordingExporter.reset();
    }
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

  public void interceptInterPartitionIntent(final int partitionId, final Intent targetIntent) {
    final var hasInterceptedPartition = new AtomicBoolean(false);
    interceptInterPartitionCommands(
        (receiverPartitionId, valueType, intent, recordKey, command) -> {
          if (hasInterceptedPartition.get()) {
            return true;
          }
          hasInterceptedPartition.set(true);
          return !(receiverPartitionId == partitionId && intent == targetIntent);
        });
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

  public ScaleClient scale() {
    return new ScaleClient(environmentRule);
  }

  public ActorScheduler actorScheduler() {
    return environmentRule.getActorScheduler();
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

  public enum ResetRecordingExporterTestWatcherMode {
    ONLY_BEFORE_AND_AFTER_ALL_TESTS,
    BEFORE_ALL_TESTS_AND_AFTER_EACH_TEST,
    BEFORE_EACH_TEST
  }

  public enum ResetRecordingExporterMode {
    AFTER_IDENTITY_SETUP,
    NO_RESET_AFTER_IDENTITY_SETUP
  }
}
