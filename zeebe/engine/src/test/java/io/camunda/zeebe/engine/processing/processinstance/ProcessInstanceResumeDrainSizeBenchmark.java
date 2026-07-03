/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

/**
 * Characterization benchmark for the process instance suspend/resume POC (#56552), track (a)
 * scenario 1: the drain-size sweep.
 *
 * <p>Not part of the regular test suite — no assertions or thresholds, only measurements printed to
 * stdout for a human to read. Run explicitly:
 *
 * <pre>
 * ./mvnw verify -pl zeebe/engine \
 *   -Dtest=ProcessInstanceResumeDrainSizeBenchmark -DskipTests=false -DskipITs -Dquickly
 * </pre>
 *
 * <p>{@link ProcessInstanceResumeProcessor} materializes its entire buffer into an in-memory {@code
 * ArrayList} and appends every drained command as a follow-up command within the single RESUME
 * processing cycle, with no chunking. This sweep finds the exact buffered-command count N at which
 * that atomic append exceeds the log's max record-batch size (thrown as {@code
 * ExceededBatchRecordSizeException}, caught with {@code processedCommandsCount == 0} since RESUME
 * is the batch's first command, and rolled back to a single rejection — see {@code
 * ProcessingStateMachine#processCommand}). The goal is the safe per-cycle chunk size for a
 * chunked-drain redesign.
 */
public final class ProcessInstanceResumeDrainSizeBenchmark {

  private static final String PROCESS_ID = "process";
  private static final String JOB_TYPE = "test";

  private static final int[] BUFFERED_COMMAND_COUNTS = {
    10, 100, 1_000, 5_000, 10_000, 50_000, 100_000
  };

  /** Scraped alongside the engine's own meters — see {@link #renderPrometheusText}. */
  private static final int METRICS_PORT = 9464;

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  private final SimpleMeterRegistry jvmRegistry = new SimpleMeterRegistry();
  private ServerSocket metricsSocket;
  private volatile boolean serving;

  @Before
  public void setUp() throws IOException {
    // RecordingExporter's blocking stream methods (limit/getFirst) throw if no new record
    // arrives within this window; the default 5s is too tight for a 100k-command drain.
    RecordingExporter.setMaximumWaitTime(Duration.ofMinutes(3).toMillis());

    new JvmMemoryMetrics().bindTo(jvmRegistry);
    new JvmGcMetrics().bindTo(jvmRegistry);

    // A plain blocking java.net.ServerSocket, not com.sun.net.httpserver.HttpServer: the latter's
    // NIO Selector.open() needs a loopback self-connect to set up its wakeup pipe, which this
    // sandbox's network stack refuses (same root cause as the protobuf-maven-plugin loopback
    // failure hit earlier in this session) — a plain blocking socket sidesteps that entirely.
    metricsSocket = new ServerSocket(METRICS_PORT);
    serving = true;
    final Thread metricsThread = new Thread(this::serveMetricsLoop, "metrics-http");
    metricsThread.setDaemon(true);
    metricsThread.start();
  }

  @After
  public void tearDown() throws IOException {
    serving = false;
    metricsSocket.close();
  }

  private void serveMetricsLoop() {
    while (serving) {
      try (Socket client = metricsSocket.accept()) {
        final BufferedReader request =
            new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
        // discard the request line and headers, we only ever serve one thing on this socket
        String line;
        while ((line = request.readLine()) != null && !line.isEmpty()) {
          // drain until the blank line terminating the HTTP request head
        }
        final byte[] body =
            (renderPrometheusText(engine.getMeterRegistry()) + renderPrometheusText(jvmRegistry))
                .getBytes(StandardCharsets.UTF_8);
        final String headers =
            "HTTP/1.1 200 OK\r\nContent-Type: text/plain; version=0.0.4\r\nContent-Length: "
                + body.length
                + "\r\nConnection: close\r\n\r\n";
        client.getOutputStream().write(headers.getBytes(StandardCharsets.UTF_8));
        client.getOutputStream().write(body);
        client.getOutputStream().flush();
      } catch (final IOException e) {
        // expected once tearDown() closes metricsSocket while accept() is blocked
      }
    }
  }

  @Test
  public void sweepDrainSize() throws InterruptedException {
    // let a local Prometheus (1s scrape interval) capture an idle baseline before load starts
    Thread.sleep(Duration.ofSeconds(3).toMillis());

    System.out.println(
        "\nN,outcome,resumeWallClockMillis,approxWrittenBatchBytes,heapDeltaBytes,peakHeapDeltaBytes");
    for (final int n : BUFFERED_COMMAND_COUNTS) {
      final ScenarioResult result = runScenario(n);
      System.out.println(result.toCsvRow());
      // pause between steps so each N shows as a distinguishable plateau in the time series,
      // rather than one indistinguishable blur across a handful of 1s scrapes
      Thread.sleep(Duration.ofSeconds(2).toMillis());
      if (!result.succeeded()) {
        // once the drain starts rejecting, larger N in the sweep will only reject the same way —
        // the flip point is already found.
        System.out.println("# flip point found at N=" + n + ", stopping sweep");
        break;
      }
    }

    // keep the target scrapeable for a bit so the dashboard can be inspected live, not just via
    // Prometheus's retained history
    Thread.sleep(Duration.ofSeconds(30).toMillis());
  }

  /**
   * Minimal generic Micrometer-to-Prometheus-text-exposition bridge, scoped to this benchmark. No
   * shared engine test infrastructure exposes a way to swap in a real {@code
   * PrometheusMeterRegistry} (the engine's meter registry is hardcoded per-log-stream in {@code
   * TestStreams}), so this reads the live meters out of the registry the engine already exposes via
   * {@code EngineRule#getMeterRegistry()} and renders them by hand instead.
   */
  private String renderPrometheusText(final MeterRegistry registry) {
    final StringBuilder text = new StringBuilder();
    for (final Meter meter : registry.getMeters()) {
      final String baseName = meter.getId().getName().replace('.', '_').replace('-', '_');
      final String tags = renderTags(meter.getId().getTags());
      for (final Measurement measurement : meter.measure()) {
        final String suffix =
            switch (measurement.getStatistic()) {
              case COUNT -> meter instanceof Counter ? "_total" : "_count";
              case TOTAL, TOTAL_TIME -> "_sum";
              case MAX -> "_max";
              case ACTIVE_TASKS -> "_active_count";
              case DURATION -> "_duration_sum";
              case VALUE, UNKNOWN -> "";
            };
        text.append(baseName)
            .append(suffix)
            .append(tags)
            .append(' ')
            .append(measurement.getValue())
            .append('\n');
      }
    }
    return text.toString();
  }

  private String renderTags(final List<Tag> tags) {
    if (tags.isEmpty()) {
      return "";
    }
    final StringBuilder sb = new StringBuilder("{");
    for (int i = 0; i < tags.size(); i++) {
      final Tag tag = tags.get(i);
      if (i > 0) {
        sb.append(',');
      }
      sb.append(tag.getKey()).append("=\"").append(tag.getValue()).append('"');
    }
    return sb.append('}').toString();
  }

  private ScenarioResult runScenario(final int bufferedCommandCount) {
    final long processInstanceKey = createServiceTaskProcessInstance();
    final Record<ProcessInstanceRecordValue> taskActivated =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.SERVICE_TASK)
            .getFirst();

    engine.processInstance().withInstanceKey(processInstanceKey).suspend();

    final ProcessInstanceRecord sampleEntry =
        bufferedCompleteElementCommand(processInstanceKey, taskActivated);
    final long approxEntryBytes = sampleEntry.getLength();

    final RecordToWrite[] commands = new RecordToWrite[bufferedCommandCount];
    for (int i = 0; i < bufferedCommandCount; i++) {
      commands[i] =
          RecordToWrite.command()
              .processInstance(
                  ProcessInstanceIntent.COMPLETE_ELEMENT,
                  bufferedCompleteElementCommand(processInstanceKey, taskActivated))
              .key(taskActivated.getKey());
    }
    engine.writeRecords(commands);
    // limit() short-circuits the (blocking) exporter stream once the Nth buffered event lands.
    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMMAND_BUFFERED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(bufferedCommandCount)
        .asList();

    System.gc();
    final Runtime runtime = Runtime.getRuntime();
    final long heapBefore = runtime.totalMemory() - runtime.freeMemory();
    final AtomicLong peakHeap = new AtomicLong(heapBefore);
    final AtomicBoolean sampling = new AtomicBoolean(true);
    final Thread sampler =
        new Thread(
            () -> {
              while (sampling.get()) {
                peakHeap.updateAndGet(
                    current -> Math.max(current, runtime.totalMemory() - runtime.freeMemory()));
              }
            });
    sampler.setDaemon(true);
    sampler.start();

    final long resumeStartNanos = System.nanoTime();
    engine.writeRecords(
        RecordToWrite.command()
            .processInstance(
                ProcessInstanceIntent.RESUME,
                new ProcessInstanceRecord().setProcessInstanceKey(processInstanceKey))
            .key(processInstanceKey));

    final boolean succeeded;
    try {
      succeeded = awaitResumeOutcome(processInstanceKey) == RecordType.EVENT;
    } finally {
      sampling.set(false);
      try {
        sampler.join();
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
    final long resumeEndNanos = System.nanoTime();

    final long heapAfter = runtime.totalMemory() - runtime.freeMemory();

    return new ScenarioResult(
        bufferedCommandCount,
        succeeded,
        Duration.ofNanos(resumeEndNanos - resumeStartNanos).toMillis(),
        approxEntryBytes * bufferedCommandCount,
        heapAfter - heapBefore,
        peakHeap.get() - heapBefore);
  }

  /**
   * Blocks (via the exporter stream's short-circuiting {@code limit(Predicate)}) until either the
   * RESUMED event or a rejection of the RESUME command appears, and returns which one it was.
   */
  private RecordType awaitResumeOutcome(final long processInstanceKey) {
    return RecordingExporter.processInstanceRecords()
        .withProcessInstanceKey(processInstanceKey)
        .limit(
            r ->
                (r.getIntent() == ProcessInstanceIntent.RESUMED
                        && r.getRecordType() == RecordType.EVENT)
                    || (r.getIntent() == ProcessInstanceIntent.RESUME
                        && r.getRecordType() == RecordType.COMMAND_REJECTION))
        .getLast()
        .getRecordType();
  }

  private long createServiceTaskProcessInstance() {
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
                .endEvent()
                .done())
        .deploy();
    return engine.processInstance().ofBpmnProcessId(PROCESS_ID).create();
  }

  /**
   * Builds a COMPLETE_ELEMENT command value for the service task, matching what job completion
   * would eventually trigger (same shape as {@code
   * ProcessInstanceSuspendResumeTest#bufferServiceTaskCompleteCommand}). Diverted into the buffer
   * as-is by {@code BpmnStreamProcessor} since the instance is suspended — it is never applied, so
   * the same element instance can safely receive this command many times.
   */
  private ProcessInstanceRecord bufferedCompleteElementCommand(
      final long processInstanceKey, final Record<ProcessInstanceRecordValue> taskActivated) {
    final ProcessInstanceRecordValue taskActivatedValue = taskActivated.getValue();
    return new ProcessInstanceRecord()
        .setBpmnElementType(BpmnElementType.SERVICE_TASK)
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionKey(taskActivatedValue.getProcessDefinitionKey())
        .setElementId("task")
        .setFlowScopeKey(taskActivatedValue.getFlowScopeKey())
        .setBpmnProcessId(PROCESS_ID)
        .setVersion(taskActivatedValue.getVersion());
  }

  private record ScenarioResult(
      int bufferedCommandCount,
      boolean succeeded,
      long resumeWallClockMillis,
      long approxWrittenBatchBytes,
      long heapDeltaBytes,
      long peakHeapDeltaBytes) {

    String toCsvRow() {
      return "%d,%s,%d,%d,%d,%d"
          .formatted(
              bufferedCommandCount,
              succeeded ? "SUCCEEDED" : "EXCEEDED_BATCH_SIZE",
              resumeWallClockMillis,
              approxWrittenBatchBytes,
              heapDeltaBytes,
              peakHeapDeltaBytes);
    }
  }
}
