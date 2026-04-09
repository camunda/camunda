/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.test.generator;

import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_INCIDENT_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_DEFINITION_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_PROCESS_INSTANCE_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_USER_TASK_INDEX_NAME;
import static io.camunda.optimize.service.db.DatabaseConstants.ZEEBE_VARIABLE_INDEX_NAME;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.value.BpmnElementType.PROCESS;

import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import io.camunda.optimize.service.db.es.OptimizeElasticsearchClient;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Orchestrates synthetic Zeebe data generation, populating raw Zeebe record indexes so the Optimize
 * importers can run without a live Zeebe broker.
 *
 * <p>This class owns only orchestration: it loops over configured process definitions, constructs
 * {@link InstanceWindow} for each instance, and delegates all record emission to collaborators.
 *
 * <p>Collaborators:
 *
 * <ul>
 *   <li>{@link GeneratorConfig} — immutable configuration and key-derivation logic
 *   <li>{@link ZeebeRecordFactory} — builds Zeebe record DTOs and wraps them as ES operations
 *   <li>{@link FlowNodeEmitter} — pure factory for per-instance record lists
 *   <li>{@link BpmnFlowParser} — parses BPMN resources into reusable {@link ProcessGraph}s
 *   <li>{@link ZeebeBulkWriter} — handles ES index lifecycle and batched bulk writes
 * </ul>
 *
 * <p>Usage:
 *
 * <pre>{@code
 * new ZeebeProcessDataGenerator(
 *     new GeneratorConfig.Builder()
 *         .instanceCount(50_000)
 *         .zeebeRecordPrefix(zeebeExtension.getZeebeRecordPrefix())
 *         .build())
 *     .generate(esClient);
 * }</pre>
 */
public class ZeebeProcessDataGenerator {

  private static final Logger LOG = LoggerFactory.getLogger(ZeebeProcessDataGenerator.class);

  private static final double ACTIVE_INSTANCE_RATE = 0.03;
  private static final double TERMINATED_INSTANCE_RATE = 0.05;
  private static final double INCIDENT_RATE = 0.05;

  private final GeneratorConfig config;
  private final Random random;
  private final ZeebeRecordFactory factory;
  private final FlowNodeEmitter emitter;

  public ZeebeProcessDataGenerator(final GeneratorConfig config) {
    this.config = config;
    random = new Random(config.seed);
    final VariableCatalogue catalogue = new VariableCatalogue(random);
    final NodeTimingSimulator timingSimulator = new NodeTimingSimulator(random);
    factory =
        new ZeebeRecordFactory(
            config.partitionId, config.positionOffset, catalogue, ProcessBpmnBuilder::bpmn);
    emitter = new FlowNodeEmitter(factory, timingSimulator, random);
  }

  // ── Public API ────────────────────────────────────────────────────────────

  /** Generates and bulk-inserts all synthetic Zeebe records. */
  public void generate(final OptimizeElasticsearchClient esClient) {
    final String[] processIds = config.resolveProcessIds();
    final ZeebeBulkWriter writer = new ZeebeBulkWriter(esClient, config.batchSize);
    requiredIndexNames().forEach(writer::ensureIndexExists);
    insertProcessDefinitions(writer, processIds);
    insertProcessInstances(writer, processIds);

    if (config.updateRate > 0.0) {
      insertVariableUpdates(writer, processIds);
    }
  }

  // ── Index setup ───────────────────────────────────────────────────────────

  private List<String> requiredIndexNames() {
    return List.of(
        zeebeIndexName(ZEEBE_PROCESS_DEFINITION_INDEX_NAME),
        zeebeIndexName(ZEEBE_PROCESS_INSTANCE_INDEX_NAME),
        zeebeIndexName(ZEEBE_VARIABLE_INDEX_NAME),
        zeebeIndexName(ZEEBE_USER_TASK_INDEX_NAME),
        zeebeIndexName(ZEEBE_INCIDENT_INDEX_NAME));
  }

  // ── Process definitions ───────────────────────────────────────────────────

  private void insertProcessDefinitions(final ZeebeBulkWriter writer, final String[] processIds) {
    final long creationTimestampMs = OffsetDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
    final List<BulkOperation> processDefinitionOps =
        IntStream.range(0, processIds.length)
            .mapToObj(
                i -> {
                  final String processId = processIds[i];
                  final long definitionKey = config.definitionKeyFor(i);
                  return factory.processDefinitionOp(processId, definitionKey, creationTimestampMs);
                })
            .toList();
    writer.write(zeebeIndexName(ZEEBE_PROCESS_DEFINITION_INDEX_NAME), processDefinitionOps);
  }

  // ── Process instances ─────────────────────────────────────────────────────

  private void insertProcessInstances(final ZeebeBulkWriter writer, final String[] processIds) {
    final OffsetDateTime endTime = OffsetDateTime.now(ZoneOffset.UTC);
    final OffsetDateTime earliestStartTime = endTime.minusMonths(config.monthsOfHistory);
    final long historicalRangeSeconds = endTime.toEpochSecond() - earliestStartTime.toEpochSecond();

    final ProcessGraph[] processGraphs = parseProcessGraphs(processIds);
    final InstanceBatches writeBatches = createWriteBatches(writer);
    final int logProgressEvery = Math.max(1, config.instanceCount / 10);

    for (int instanceIndex = 0; instanceIndex < config.instanceCount; instanceIndex++) {
      final int definitionIndex = instanceIndex % processIds.length;
      final long instanceKey = config.instanceKeyOffset + instanceIndex;
      final long definitionKey = config.definitionKeyFor(definitionIndex);
      final String processId = processIds[definitionIndex];
      final InstanceContext instanceContext =
          new InstanceContext(instanceKey, definitionKey, processId);
      final List<FlowNode> executionPath = processGraphs[definitionIndex].walk(random);
      final InstanceWindow instanceWindow =
          sampleInstanceWindow(earliestStartTime, historicalRangeSeconds);

      final var instanceOps = generateInstance(instanceContext, executionPath, instanceWindow);
      writeBatches.addAll(instanceOps);
      writeBatches.flushIfNeeded(writer);

      final int completedCount = instanceIndex + 1;
      final boolean isProgressCheckpoint =
          completedCount % logProgressEvery == 0 || completedCount == config.instanceCount;
      if (isProgressCheckpoint) {
        logProgress(completedCount, writeBatches);
      }
    }

    writeBatches.flush(writer);
    logCompletion(writeBatches);
  }

  // ── Variable updates ──────────────────────────────────────────────────────

  /**
   * Emits {@code UPDATED} variable records for a random fraction of the generated instances. These
   * records land at higher positions than the {@code CREATED} records, so the Optimize importer
   * processes them in a later round and merges the new values into existing docs — producing the
   * "deleted" Lucene segments visible in index stats.
   */
  private void insertVariableUpdates(final ZeebeBulkWriter writer, final String[] processIds) {
    final int updateCount = (int) Math.round(config.instanceCount * config.updateRate);
    LOG.info(
        "Emitting UPDATED variable records for {} instances (updateRate={})",
        updateCount,
        config.updateRate);

    final ZeebeBulkWriter.IndexBatch variableBatch =
        writer.newBatch(zeebeIndexName(ZEEBE_VARIABLE_INDEX_NAME));
    final long updateTimestampMs = OffsetDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();

    for (int i = 0; i < updateCount; i++) {
      final int definitionIndex = i % processIds.length;
      final long instanceKey = config.instanceKeyOffset + i;
      final long definitionKey = config.definitionKeyFor(definitionIndex);
      final String processId = processIds[definitionIndex];
      final InstanceContext ctx = new InstanceContext(instanceKey, definitionKey, processId);

      variableBatch.addAll(emitter.variableUpdateOps(ctx, updateTimestampMs));
      writer.flushIfNeeded(variableBatch);
    }
    writer.flush(variableBatch);
    LOG.info("UPDATED variable records flushed: {}", variableBatch.flushedCount());
  }

  private ProcessGraph[] parseProcessGraphs(final String[] processIds) {
    return Arrays.stream(processIds).map(BpmnFlowParser::parse).toArray(ProcessGraph[]::new);
  }

  private InstanceBatches createWriteBatches(final ZeebeBulkWriter writer) {
    final ZeebeBulkWriter.IndexBatch processInstanceBatch =
        writer.newBatch(zeebeIndexName(ZEEBE_PROCESS_INSTANCE_INDEX_NAME));
    final ZeebeBulkWriter.IndexBatch variableBatch =
        writer.newBatch(zeebeIndexName(ZEEBE_VARIABLE_INDEX_NAME));
    final ZeebeBulkWriter.IndexBatch userTaskBatch =
        writer.newBatch(zeebeIndexName(ZEEBE_USER_TASK_INDEX_NAME));
    final ZeebeBulkWriter.IndexBatch incidentBatch =
        writer.newBatch(zeebeIndexName(ZEEBE_INCIDENT_INDEX_NAME));
    return new InstanceBatches(processInstanceBatch, variableBatch, userTaskBatch, incidentBatch);
  }

  private InstanceOps generateInstance(
      final InstanceContext instanceContext,
      final List<FlowNode> executionPath,
      final InstanceWindow instanceWindow) {

    final ElementRecord processElement =
        new ElementRecord(instanceContext.instanceKey(), instanceContext.processId(), PROCESS, -1L);

    final LifecycleEvent processStart =
        new LifecycleEvent(ELEMENT_ACTIVATING, instanceWindow.startMs());
    final BulkOperation processStartOp =
        factory.processInstanceOp(instanceContext, processElement, processStart);

    final FlowNodeEmitter.FlowNodeOps flowNodeOps =
        emitter.flowNodeOps(instanceContext, executionPath, instanceWindow);

    final Optional<BulkOperation> processEndOp =
        buildProcessEndOp(instanceContext, processElement, instanceWindow);

    final List<BulkOperation> processInstanceOps =
        Stream.of(Stream.of(processStartOp), flowNodeOps.pi().stream(), processEndOp.stream())
            .flatMap(s -> s)
            .toList();

    final List<BulkOperation> variableOps =
        emitter.variableOps(instanceContext, instanceWindow.endMs());
    final List<BulkOperation> incidentOps =
        instanceWindow.hasIncident()
            ? emitter.incidentOps(instanceContext, executionPath, instanceWindow)
            : List.of();

    final var userTasksOps = flowNodeOps.ut();
    return new InstanceOps(processInstanceOps, variableOps, userTasksOps, incidentOps);
  }

  private Optional<BulkOperation> buildProcessEndOp(
      final InstanceContext ctx,
      final ElementRecord processElement,
      final InstanceWindow instanceWindow) {
    if (instanceWindow.isActive()) {
      return Optional.empty();
    }
    final LifecycleEvent processEnd =
        new LifecycleEvent(instanceWindow.endIntent(), instanceWindow.endMs());
    final BulkOperation endOp = factory.processInstanceOp(ctx, processElement, processEnd);
    return Optional.of(endOp);
  }

  // ── Instance window sampling ──────────────────────────────────────────────

  private InstanceWindow sampleInstanceWindow(
      final OffsetDateTime earliestStartTime, final long historicalRangeSeconds) {

    final boolean isActive = random.nextDouble() < ACTIVE_INSTANCE_RATE;
    final boolean isTerminated = !isActive && random.nextDouble() < TERMINATED_INSTANCE_RATE;
    final boolean hasIncident = !isActive && random.nextDouble() < INCIDENT_RATE;

    final long randomOffsetSeconds = (long) (random.nextDouble() * historicalRangeSeconds);
    final OffsetDateTime instanceStart = earliestStartTime.plusSeconds(randomOffsetSeconds);

    final long durationMinutes = 10L + (long) (random.nextDouble() * 4310);
    final OffsetDateTime instanceEnd = isActive ? null : instanceStart.plusMinutes(durationMinutes);

    final long startMs = instanceStart.toInstant().toEpochMilli();
    final long endMs = instanceEnd != null ? instanceEnd.toInstant().toEpochMilli() : startMs;

    return new InstanceWindow(startMs, endMs, isActive, isTerminated, hasIncident);
  }

  // ── Logging ───────────────────────────────────────────────────────────────

  private void logProgress(final int completedCount, final InstanceBatches writeBatches) {
    final int progressPercent = 100 * completedCount / config.instanceCount;
    LOG.info(
        "Progress: {}/{} instances ({} %) — pi={}, var={}, ut={}, inc={} records so far",
        completedCount,
        config.instanceCount,
        progressPercent,
        writeBatches.processInstance().totalCount(),
        writeBatches.variable().totalCount(),
        writeBatches.userTask().totalCount(),
        writeBatches.incident().totalCount());
  }

  private void logCompletion(final InstanceBatches writeBatches) {
    LOG.info(
        "Generation complete: {} instances — pi={}, var={}, ut={}, inc={} records inserted",
        config.instanceCount,
        writeBatches.processInstance().flushedCount(),
        writeBatches.variable().flushedCount(),
        writeBatches.userTask().flushedCount(),
        writeBatches.incident().flushedCount());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String zeebeIndexName(final String suffix) {
    return config.zeebeRecordPrefix + "-" + suffix;
  }

  // ── Value objects ─────────────────────────────────────────────────────────

  /**
   * Immutable set of records produced for one process instance, grouped by target index. Returned
   * by {@link #generateInstance} — no mutable state, no side effects.
   */
  private record InstanceOps(
      List<BulkOperation> processInstance,
      List<BulkOperation> variable,
      List<BulkOperation> userTask,
      List<BulkOperation> incident) {}

  /**
   * Groups the per-index write batches for one generation run.
   *
   * <p>Owns only the ES batch lifecycle (size-based flushing and final flush). Records are added
   * via {@link #addAll} from a single call site in the generation loop.
   *
   * <p>To add a new Zeebe index: add one field here and one entry in {@link #all()}.
   */
  private record InstanceBatches(
      ZeebeBulkWriter.IndexBatch processInstance,
      ZeebeBulkWriter.IndexBatch variable,
      ZeebeBulkWriter.IndexBatch userTask,
      ZeebeBulkWriter.IndexBatch incident) {

    void addAll(final InstanceOps ops) {
      processInstance.addAll(ops.processInstance());
      variable.addAll(ops.variable());
      userTask.addAll(ops.userTask());
      incident.addAll(ops.incident());
    }

    void flushIfNeeded(final ZeebeBulkWriter writer) {
      all().forEach(writer::flushIfNeeded);
    }

    void flush(final ZeebeBulkWriter writer) {
      all().forEach(writer::flush);
    }

    private Stream<ZeebeBulkWriter.IndexBatch> all() {
      return Stream.of(processInstance, variable, userTask, incident);
    }
  }
}
