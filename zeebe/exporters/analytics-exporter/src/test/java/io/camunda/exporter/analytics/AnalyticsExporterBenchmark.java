/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import io.camunda.zeebe.exporter.test.ExporterTestConfiguration;
import io.camunda.zeebe.exporter.test.ExporterTestContext;
import io.camunda.zeebe.exporter.test.ExporterTestController;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.test.broker.protocol.ProtocolFactory;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.data.LogRecordData;
import io.opentelemetry.sdk.logs.export.LogRecordExporter;
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor;
import java.util.Collection;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * JMH microbenchmarks for the {@link AnalyticsExporter} hot paths.
 *
 * <p>Run manually via the main method or: {@code mvn verify -pl zeebe/exporters/analytics-exporter
 * -Dtest=AnalyticsExporterBenchmark -DskipTests=false}
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class AnalyticsExporterBenchmark {

  /** No-op exporter that discards all records — safe for unbounded benchmark loops. */
  private static final LogRecordExporter NOOP_EXPORTER =
      new LogRecordExporter() {
        @Override
        public CompletableResultCode export(final Collection<LogRecordData> logs) {
          return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
          return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
          return CompletableResultCode.ofSuccess();
        }
      };

  private AnalyticsExporter exporterWithSigning;
  private AnalyticsExporter exporterWithoutSigning;
  private ExporterTestController controllerSigning;
  private ExporterTestController controllerNoSigning;
  private Record<?> piCreatedRecord;
  private Record<?> jobRecord;

  @Setup
  public void setUp() {
    final var factory = new ProtocolFactory();

    // Pre-generate records once — avoids allocation noise inside benchmarks
    piCreatedRecord =
        factory.generateRecord(
            ValueType.PROCESS_INSTANCE_CREATION,
            r ->
                r.withRecordType(RecordType.EVENT)
                    .withIntent(ProcessInstanceCreationIntent.CREATED));
    jobRecord = factory.generateRecord(ValueType.JOB);

    controllerSigning = new ExporterTestController();
    controllerNoSigning = new ExporterTestController();

    exporterWithSigning = createExporter(controllerSigning, true);
    exporterWithoutSigning = createExporter(controllerNoSigning, false);
  }

  /**
   * Hot path: record with no matching handler. Represents the vast majority (~99 %) of records seen
   * by the exporter in production.
   */
  @Benchmark
  public void exportUnmatchedRecord() {
    exporterWithSigning.export(jobRecord);
  }

  /** Handler match with HMAC signing enabled (default). Measures crypto overhead per export. */
  @Benchmark
  public void exportPiCreatedWithSigning() {
    exporterWithSigning.export(piCreatedRecord);
  }

  /** Handler match with signing disabled (fingerprint-only). Baseline for comparison. */
  @Benchmark
  public void exportPiCreatedWithoutSigning() {
    exporterWithoutSigning.export(piCreatedRecord);
  }

  @TearDown
  public void tearDown() {
    exporterWithSigning.close();
    exporterWithoutSigning.close();
  }

  private static AnalyticsExporter createExporter(
      final ExporterTestController controller, final boolean signing) {
    final var noopManager =
        new OtelSdkManager() {
          @Override
          protected SdkLoggerProvider createLoggerProvider(
              final AnalyticsExporterConfig cfg, final AnalyticsExporterContext context) {
            return SdkLoggerProvider.builder()
                .setResource(OtelSdkManager.buildResource(context))
                .addLogRecordProcessor(SimpleLogRecordProcessor.create(NOOP_EXPORTER))
                .build();
          }
        };
    final var config = new AnalyticsExporterConfig().setSigning(signing);
    final var context =
        new ExporterTestContext()
            .setConfiguration(new ExporterTestConfiguration<>("analytics-bench", config))
            .setClusterId("bench-cluster")
            .setPartitionId(1)
            .setLicenseKey("bench-license-key");
    final var exporter = new AnalyticsExporter(noopManager);
    exporter.configure(context);
    exporter.open(controller);
    return exporter;
  }

  /**
   * Run benchmarks from IntelliJ via the play button. Skipped in CI by the {@code
   * EnabledIfSystemProperty} condition — pass {@code -Dbenchmark=true} to enable.
   */
  @Test
  @EnabledIfSystemProperty(named = "benchmark", matches = "true")
  void runBenchmarks() throws Exception {
    final var options =
        new org.openjdk.jmh.runner.options.OptionsBuilder()
            .include(AnalyticsExporterBenchmark.class.getSimpleName())
            .warmupIterations(5)
            .measurementIterations(10)
            .forks(0) // no fork — run in same JVM so IntelliJ can debug
            .build();
    new org.openjdk.jmh.runner.Runner(options).run();
  }
}
