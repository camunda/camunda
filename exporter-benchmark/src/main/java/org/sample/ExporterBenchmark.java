/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package org.sample;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.protocol.record.Record;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.Warmup;

public class ExporterBenchmark {

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @Warmup(iterations = 0, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 1, time = 30, timeUnit = TimeUnit.SECONDS)
  @Fork(value = 1, warmups = 0)
  public void testPrototypeExporter(OperateElasticsearchExporterState exporterState) {
    List<Record<?>> records = exporterState.buildProcessInstanceRecords();

    Exporter exporter = exporterState.getExporter();
    records.forEach(r -> exporter.export(r));
  }

  @Benchmark
  @BenchmarkMode(Mode.Throughput)
  @Warmup(iterations = 1, time = 5, timeUnit = TimeUnit.SECONDS)
  @Measurement(iterations = 4, time = 30, timeUnit = TimeUnit.SECONDS)
  @Fork(value = 1, warmups = 1)
  public void testDefaultExporter(ElasticsearchExporterState exporterState) {
    List<Record<?>> records = exporterState.buildProcessInstanceRecords();

    Exporter exporter = exporterState.getExporter();
    records.forEach(r -> exporter.export(r));
  }
}
