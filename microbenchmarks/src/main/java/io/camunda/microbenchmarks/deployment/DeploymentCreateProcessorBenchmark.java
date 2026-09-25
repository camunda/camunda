/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.deployment;

import static io.camunda.microbenchmarks.deployment.LargeProcess.RESOURCE_NAME;
import static io.camunda.microbenchmarks.deployment.LargeProcess.VERSION_PLACEHOLDER;

import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.el.ExpressionLanguageFactory;
import io.camunda.zeebe.el.ExpressionLanguageMetrics;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.DistributionMetrics;
import io.camunda.zeebe.engine.metrics.ProcessDefinitionMetrics;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.clock.ZeebeFeelEngineClock;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.deployment.DeploymentCreateProcessor;
import io.camunda.zeebe.engine.processing.distribution.CommandDistributionBehavior;
import io.camunda.zeebe.engine.processing.expression.ScopedEvaluationContext;
import io.camunda.zeebe.engine.processing.identity.authorization.CslAuthorizationCheck;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.ProcessingDbState;
import io.camunda.zeebe.engine.state.appliers.EventAppliers;
import io.camunda.zeebe.engine.state.message.TransientPendingMessageStartProcessInstanceAskState;
import io.camunda.zeebe.engine.state.message.TransientPendingSubscriptionState;
import io.camunda.zeebe.engine.state.routing.RoutingInfo;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.builder.AbstractFlowNodeBuilder;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.PostCommitTask;
import io.camunda.zeebe.stream.api.ProcessingResult;
import io.camunda.zeebe.stream.api.ProcessingResultBuilder;
import io.camunda.zeebe.stream.api.StreamClock;
import io.camunda.zeebe.stream.impl.records.RecordBatch;
import io.camunda.zeebe.stream.impl.records.UnwrittenRecord;
import io.camunda.zeebe.stream.impl.state.DbKeyGenerator;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.FeatureFlags;
import io.camunda.zeebe.util.FileUtil;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.InstantSource;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * Benchmarks {@link DeploymentCreateProcessor} processing a deployment of a large BPMN process,
 * against a real RocksDB backed state, process cache and event appliers. Unlike {@link
 * LargeProcessDeploymentBenchmark}, this covers what the processor does after transforming the
 * resource, e.g. the process lookups in {@code createTimerIfTimerStartEvent}.
 *
 * <p>Every invocation deploys a new version of the same process, so the duplicate check compares
 * against the previous version as it does in production. Each version stays in the state for the
 * rest of the trial.
 *
 * <p>Collaborators outside the deployment path are left out: the command is internal, so no
 * authorization check runs; there is a single partition, so the deployment is not distributed; and
 * written records are collected in memory instead of being appended to a log.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(
    value = 1,
    jvmArgsAppend = {"-Xms4G", "-Xmx4G", "--add-opens=java.base/jdk.internal.misc=ALL-UNNAMED"})
@State(Scope.Benchmark)
public class DeploymentCreateProcessorBenchmark {

  private static final int PARTITION_ID = 1;

  @Param({"100", "1000"})
  private int serviceTaskCount;

  private Path dbDirectory;
  private ZeebeDb<ZbColumnFamilies> zeebeDb;
  private TransactionContext transactionContext;
  private CollectingResultBuilder resultBuilder;
  private DeploymentCreateProcessor processor;
  private String resourceTemplate;
  private int version;
  private UnwrittenRecord command;

  public static void main(final String[] args) throws RunnerException {
    final var options =
        new OptionsBuilder()
            .addProfiler("gc")
            .include(DeploymentCreateProcessorBenchmark.class.getSimpleName())
            .build();
    new Runner(options).run();
  }

  @Setup
  public void setup() throws Exception {
    dbDirectory = Files.createTempDirectory("deployment-benchmark");
    zeebeDb =
        new ZeebeRocksDbFactory<ZbColumnFamilies>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(),
                new AccessMetricsConfiguration(Kind.NONE),
                SimpleMeterRegistry::new)
            .createDb(dbDirectory.toFile());
    transactionContext = zeebeDb.createContext();
    processor = createProcessor();
    resourceTemplate = Bpmn.convertToString(createLargeProcess(serviceTaskCount));
  }

  @TearDown
  public void tearDown() throws Exception {
    zeebeDb.close();
    FileUtil.deleteFolder(dbDirectory);
  }

  @Setup(Level.Invocation)
  public void setupCommand() {
    // identical resources are rejected as duplicates without being transformed, so every
    // invocation deploys a resource with a different process name
    final var resource =
        resourceTemplate
            .replace(VERSION_PLACEHOLDER, String.valueOf(++version))
            .getBytes(StandardCharsets.UTF_8);
    final var deployment = new DeploymentRecord();
    deployment.resources().add().setResourceName(RESOURCE_NAME).setResource(resource);
    command =
        new UnwrittenRecord(
            -1,
            PARTITION_ID,
            deployment,
            new RecordMetadata()
                .recordType(RecordType.COMMAND)
                .valueType(ValueType.DEPLOYMENT)
                .intent(DeploymentIntent.CREATE));
    resultBuilder.reset();
  }

  @Benchmark
  public int measureDeployment() throws Exception {
    final var transaction = transactionContext.getCurrentTransaction();
    transaction.run(() -> processor.processRecord(command));
    transaction.commit();
    return resultBuilder.recordCount();
  }

  private DeploymentCreateProcessor createProcessor() {
    final var clock = StreamClock.controllable(InstantSource.system());
    final var config = new EngineConfiguration();
    final var meterRegistry = new SimpleMeterRegistry();
    final var expressionLanguageMetrics = ExpressionLanguageMetrics.noop();
    final var keyGenerator = new DbKeyGenerator(PARTITION_ID, zeebeDb, transactionContext);
    final var processingState =
        new ProcessingDbState(
            PARTITION_ID,
            zeebeDb,
            transactionContext,
            keyGenerator,
            new TransientPendingSubscriptionState(),
            new TransientPendingSubscriptionState(),
            new TransientPendingMessageStartProcessInstanceAskState(),
            config,
            clock,
            expressionLanguageMetrics);

    resultBuilder = new CollectingResultBuilder();
    final var eventAppliers = new EventAppliers();
    final var writers = new Writers(() -> resultBuilder, eventAppliers);
    eventAppliers.registerEventAppliers(processingState);

    final var routingInfo = RoutingInfo.forStaticPartitions(1);
    final var expressionProcessor =
        new ExpressionProcessor(
            ExpressionLanguageFactory.createExpressionLanguage(
                new ZeebeFeelEngineClock(clock), expressionLanguageMetrics),
            ScopedEvaluationContext.NONE_INSTANCE,
            Duration.ofSeconds(1));
    // only needed for timer start events, which the benchmarked process doesn't have
    final var catchEventBehavior =
        new CatchEventBehavior(
            processingState,
            keyGenerator,
            expressionProcessor,
            null,
            writers,
            null,
            routingInfo,
            clock,
            new TransientPendingSubscriptionState(),
            config.getMaxNameFieldLength(),
            false);
    // the processor only uses these two behaviors
    final var bpmnBehaviors =
        (BpmnBehaviors)
            Proxy.newProxyInstance(
                BpmnBehaviors.class.getClassLoader(),
                new Class<?>[] {BpmnBehaviors.class},
                (proxy, method, args) ->
                    switch (method.getName()) {
                      case "catchEventBehavior" -> catchEventBehavior;
                      case "expressionProcessor" -> expressionProcessor;
                      default -> throw new UnsupportedOperationException(method.getName());
                    });

    return new DeploymentCreateProcessor(
        processingState,
        bpmnBehaviors,
        writers,
        keyGenerator,
        FeatureFlags.createDefault(),
        new CommandDistributionBehavior(
            processingState.getDistributionState(),
            writers,
            PARTITION_ID,
            routingInfo,
            null,
            new DistributionMetrics(meterRegistry),
            clock),
        config,
        clock,
        new CslAuthorizationCheck(
            null, null, EngineSecurityConfigurations.unauthenticatedAndUnauthorized()),
        expressionLanguageMetrics,
        new ProcessDefinitionMetrics(meterRegistry, processingState.getProcessState()));
  }

  /**
   * Creates a sequential process of service tasks, each with FEEL input/output mappings and a job
   * type expression, since expressions are parsed and validated on deployment too. The process name
   * contains a placeholder to make each deployed version unique.
   */
  public static BpmnModelInstance createLargeProcess(final int serviceTaskCount) {
    AbstractFlowNodeBuilder<?, ?> builder =
        Bpmn.createExecutableProcess("large-process")
            .name("large-process-" + VERSION_PLACEHOLDER)
            .startEvent();
    for (int i = 0; i < serviceTaskCount; i++) {
      final var index = i;
      builder =
          builder.serviceTask(
              "task-" + i,
              t ->
                  t.zeebeJobTypeExpression("\"task-\" + string(" + index + ")")
                      .zeebeInputExpression("order.items[" + (index + 1) + "]", "item")
                      .zeebeInputExpression("if item.price > 100 then true else false", "premium")
                      .zeebeOutputExpression("result.status", "status" + index));
    }
    return builder.endEvent().done();
  }

  /**
   * Serializes the written records into a record batch like the stream processor does, without
   * appending them to a log afterwards.
   */
  private static final class CollectingResultBuilder implements ProcessingResultBuilder {

    private RecordBatch recordBatch;

    void reset() {
      recordBatch = new RecordBatch((count, size) -> true);
    }

    int recordCount() {
      return recordBatch.entries().size();
    }

    @Override
    public Either<RuntimeException, ProcessingResultBuilder> appendRecordReturnEither(
        final long key, final RecordValue value, final RecordMetadata metadata) {
      final var unifiedValue = (UnifiedRecordValue) value;
      return recordBatch
          .appendRecord(key, metadata.valueType(unifiedValue.valueType()), -1, unifiedValue)
          .map(ignored -> this);
    }

    @Override
    public ProcessingResultBuilder withResponse(
        final RecordType type,
        final long key,
        final Intent intent,
        final UnpackedObject value,
        final ValueType valueType,
        final RejectionType rejectionType,
        final String rejectionReason,
        final long requestId,
        final int requestStreamId) {
      return this;
    }

    @Override
    public ProcessingResultBuilder appendPostCommitTask(final PostCommitTask task) {
      return this;
    }

    @Override
    public ProcessingResultBuilder resetPostCommitTasks() {
      return this;
    }

    @Override
    public ProcessingResult build() {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean canWriteEventOfLength(final int eventLength) {
      return true;
    }
  }
}
