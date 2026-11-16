/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.perf;

import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.engine.state.EngineKeyIndexPolicies;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.logstreams.log.LogStreamReader;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.RecordMetadata;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Plain main alternative to the JMH benchmarks: one engine partition on a single stream processor
 * thread, no broker, no network and no exporter. Commands are written in batches and the driver
 * waits on the processed position of the stream processor, reading the job keys from the log. Each
 * instance is created, its job activated and completed. Compare RocksDB and write-behind by
 * toggling the environment variable {@code ZEEBE_DB_WRITE_BEHIND_ENABLED}. Run with the Gradle task
 * {@code engineLoad}.
 *
 * <p>Environment variables: {@code LOAD_INSTANCES} [20000], {@code LOAD_WARMUP} [10% of the
 * instances], {@code LOAD_PREFILL} [0] instances created and left open before the run, to build up
 * state, {@code LOAD_BATCH} [100] commands written before waiting, {@code LOAD_REVISIT} [false] to
 * keep the prefilled instances open and complete the oldest open instance for each new one, {@code
 * ZEEBE_DB_ROCKSDB_STATISTICS} [false] to print the RocksDB metrics after the run, {@code
 * ZEEBE_DB_ACCESS_METRICS} [false] for RocksDB access counts per column family, {@code
 * ZEEBE_DB_KEY_INDEX} [default] and {@code ZEEBE_DB_KEY_INDEX_MAX_KEYS} for the write-behind key
 * index, see {@link EngineKeyIndexPolicies#parse}. The settings are printed first, on a line
 * starting with {@code LOAD-TEST-CONFIG}. The write-behind counters per column family are always
 * printed (lines starting with {@code CF}), their sums on a line starting with {@code CF-TOTAL},
 * and the store-wide ones, like the key index loads and handovers, on a line starting with {@code
 * WRITE-BEHIND}. The summary is printed on one line starting with {@code LOAD-TEST-RESULT}.
 */
public final class EngineLoadMain {

  private static final String JOB_TYPE = "task";
  private static final String WRITE_BEHIND_PREFIX = "zeebe.writebehind.";

  private final TestEngine engine;
  private final LogStreamReader reader;
  private final int batch;
  private final JobBatchRecord activated = new JobBatchRecord();
  private final RecordMetadata metadata = new RecordMetadata();
  // the writer serializes a command before it returns, so one instance of each serves all commands
  private final ProcessInstanceCreationRecord creation =
      new ProcessInstanceCreationRecord().setBpmnProcessId("process");
  private final JobRecord completion = new JobRecord();
  private final AuthInfo authorizations =
      AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true);
  private final boolean revisit;
  private final Deque<Long> open = new ArrayDeque<>();
  private long lastPosition;

  private EngineLoadMain(final TestEngine engine, final int batch, final boolean revisit) {
    this.revisit = revisit;
    this.engine = engine;
    this.batch = batch;
    reader = engine.newLogReader();
  }

  public static void main(final String[] args) throws Exception {
    // debug logging of every processed command dominates the runtime otherwise
    Configurator.setLevel("io.camunda.zeebe", Level.WARN);
    final int instances = Integer.parseInt(env("LOAD_INSTANCES", "20000"));
    final int warmup = Integer.parseInt(env("LOAD_WARMUP", "" + instances / 10));
    final int prefill = Integer.parseInt(env("LOAD_PREFILL", "0"));
    final int batch = Integer.parseInt(env("LOAD_BATCH", "100"));
    final boolean statistics = Boolean.parseBoolean(System.getenv("ZEEBE_DB_ROCKSDB_STATISTICS"));
    final boolean revisit = Boolean.parseBoolean(env("LOAD_REVISIT", "false"));
    final boolean writeBehind =
        Boolean.parseBoolean(System.getenv("ZEEBE_DB_WRITE_BEHIND_ENABLED"));
    final var keyIndex = writeBehind ? EngineKeyIndexPolicies.fromEnvironment() : null;
    final var mode = keyIndex == null ? "rocksdb" : "write-behind keyIndex=" + keyIndex.name();
    System.out.printf(
        "LOAD-TEST-CONFIG mode=%s revisit=%s instances=%d warmup=%d prefill=%d batch=%d%s%n",
        mode,
        revisit,
        instances,
        warmup,
        prefill,
        batch,
        keyIndex == null
            ? ""
            : " keyIndexMaxKeys=%d keyIndexPolicies=%s"
                .formatted(DefaultZeebeDbFactory.keyIndexMaxKeys(), keyIndex.describePolicies()));

    final var context = TestEngine.createTestContext();
    try {
      final var load =
          new EngineLoadMain(
              TestEngine.createSinglePartitionEngineWithoutExporter(context), batch, revisit);
      load.deploy();

      for (int done = 0; done < prefill; done += batch) {
        load.prefill(Math.min(batch, prefill - done));
        if (done % 10_000 == 0) {
          System.out.printf("prefill %d/%d%n", done, prefill);
        }
      }

      load.run(warmup);
      final var baseline = load.engine.columnFamilyMetrics();
      final var dbBaseline = load.engine.dbMetrics();
      final var start = System.nanoTime();
      load.run(instances);
      final var seconds = Duration.ofNanos(System.nanoTime() - start).toNanos() / 1e9;
      System.out.printf(
          "LOAD-TEST-RESULT mode=%s revisit=%s instances=%d prefill=%d batch=%d duration=%.2fs throughput=%.1f instances/s%n",
          mode, revisit, instances, prefill, batch, seconds, instances / seconds);
      // a snapshot waits until the write-behind writer has flushed everything, so the counters
      // and RocksDB's statistics cover all writes of the run
      load.engine.commandWriter().snapshot(1);
      final var dbMetrics = load.engine.dbMetrics();
      if (statistics) {
        // gauges that hold a level, like memory use, are printed as their change as well
        dbMetrics.forEach(
            (name, value) -> {
              final var change = value - dbBaseline.getOrDefault(name, 0.0);
              if (change != 0 && !name.startsWith(WRITE_BEHIND_PREFIX)) {
                System.out.printf("ROCKSDB %s=%.0f%n", name, change);
              }
            });
      }
      if (writeBehind) {
        // the key indexes load during the warmup or the prefill, so print the whole run as well
        System.out.println("WRITE-BEHIND " + writeBehindCounters(dbMetrics, dbBaseline));
        System.out.println("WRITE-BEHIND-SINCE-START " + writeBehindCounters(dbMetrics, Map.of()));
      }
      final var columnFamilies = load.engine.columnFamilyMetrics();
      TestEngine.formatColumnFamilyMetrics(columnFamilies, baseline)
          .forEach(m -> System.out.println("CF " + m));
      System.out.println("CF-TOTAL " + sumOverColumnFamilies(columnFamilies, baseline));
    } finally {
      context.close();
    }
  }

  private void deploy() {
    final var deployment = new DeploymentRecord();
    deployment
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(
            wrapString(
                Bpmn.convertToString(
                    Bpmn.createExecutableProcess("process")
                        .startEvent()
                        .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                        .endEvent()
                        .done())));
    engine.awaitProcessed(engine.commandWriter().writeCommand(DeploymentIntent.CREATE, deployment));
  }

  /** Creates instances that stay open; in revisit mode their jobs are activated and remembered. */
  private void prefill(final int count) {
    writeCreates(count);
    if (revisit) {
      open.addAll(activateJobs(count));
    } else {
      engine.awaitProcessed(lastPosition);
    }
  }

  // One wait per batch, on the activation. The log is ordered, so the activation sees the
  // instances created before it, and the completes of the previous batch run before the next one.
  // In revisit mode each batch completes the oldest open instances instead of the new ones, so the
  // prefilled state is read again, long after it was written.
  private void run(final int count) {
    var size = Math.min(batch, count);
    writeCreates(size);
    var keys = activateJobs(size);
    for (int done = size; ; done += size) {
      completeBatch(keys);
      if (done >= count) {
        break;
      }
      size = Math.min(batch, count - done);
      writeCreates(size);
      keys = activateJobs(size);
    }
    engine.awaitProcessed(lastPosition);
  }

  private void completeBatch(final List<Long> activatedKeys) {
    if (!revisit) {
      activatedKeys.forEach(this::complete);
      return;
    }
    open.addAll(activatedKeys);
    for (int i = 0; i < activatedKeys.size(); i++) {
      complete(open.pollFirst());
    }
  }

  private void writeCreates(final int count) {
    for (int i = 0; i < count; i++) {
      lastPosition =
          engine
              .commandWriter()
              .writeCommandOnPartition(
                  1,
                  r ->
                      r.intent(ProcessInstanceCreationIntent.CREATE)
                          .event(creation)
                          .authorizations(authorizations));
    }
  }

  private List<Long> activateJobs(final int count) {
    final var command = new JobBatchRecord();
    command.setType(JOB_TYPE).setWorker("load").setTimeout(60_000).setMaxJobsToActivate(count);
    engine.awaitProcessed(engine.commandWriter().writeCommand(JobBatchIntent.ACTIVATE, command));
    final var keys = new ArrayList<Long>(count);
    while (keys.size() < count) {
      if (!reader.hasNext()) {
        throw new IllegalStateException(
            "expected " + count + " activated jobs, got " + keys.size());
      }
      final var event = reader.next();
      metadata.reset();
      event.readMetadata(metadata);
      if (metadata.getValueType() == ValueType.JOB_BATCH
          && metadata.getIntent() == JobBatchIntent.ACTIVATED) {
        event.readValue(activated);
        activated.jobKeys().forEach(k -> keys.add(k.getValue()));
      }
    }
    return keys;
  }

  private void complete(final long jobKey) {
    lastPosition =
        engine
            .commandWriter()
            .writeCommand(
                jobKey, JobIntent.COMPLETE, completion, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  }

  /** The store-wide write-behind counters, zeros included, as the change since {@code baseline}. */
  private static String writeBehindCounters(
      final Map<String, Double> current, final Map<String, Double> baseline) {
    return current.entrySet().stream()
        .filter(e -> e.getKey().startsWith(WRITE_BEHIND_PREFIX))
        .map(
            e ->
                "%s=%.0f"
                    .formatted(
                        // a timer's first measurement is its count
                        e.getKey()
                            .substring(WRITE_BEHIND_PREFIX.length())
                            .replace("flush.duration", "flushes"),
                        e.getValue() - baseline.getOrDefault(e.getKey(), 0.0)))
        .collect(Collectors.joining(" "));
  }

  private static String sumOverColumnFamilies(
      final Map<String, Map<String, Double>> current,
      final Map<String, Map<String, Double>> baseline) {
    final var totals = new TreeMap<String, Double>();
    current.forEach(
        (columnFamily, metrics) ->
            metrics.forEach(
                (metric, value) ->
                    totals.merge(
                        metric,
                        value
                            - baseline
                                .getOrDefault(columnFamily, Map.of())
                                .getOrDefault(metric, 0.0),
                        Double::sum)));
    return totals.entrySet().stream()
        .map(e -> "%s=%.0f".formatted(e.getKey(), e.getValue()))
        .collect(Collectors.joining(" "));
  }

  private static String env(final String name, final String defaultValue) {
    final var value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
