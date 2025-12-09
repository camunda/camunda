/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.config.AppCfg;
import io.camunda.zeebe.config.BenchmarkCfg;
import io.camunda.zeebe.util.ProcessInstanceUtil;
import io.camunda.zeebe.util.ProcessUtil;
import io.camunda.zeebe.util.logging.ThrottledLogger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Benchmark extends App {

  private static final Logger THROTTLED_LOGGER =
      new ThrottledLogger(LoggerFactory.getLogger(Benchmark.class), Duration.ofSeconds(5));
  private static final Logger LOG = LoggerFactory.getLogger(Benchmark.class);
  private final BenchmarkCfg benchmarkCfg;

  Benchmark(final AppCfg config) {
    super(config);
    benchmarkCfg = config.getBenchmark();
  }

  @Override
  public void run() {
    final CamundaClient camundaClient = createCamundaClient();

    switch (benchmarkCfg.getName()) {
      case "pi-creation-latency" -> executePICreationLatencyBenchmark(camundaClient);
      case "none" -> {}
      default -> throw new IllegalArgumentException("Unknown benchmark: " + benchmarkCfg.getName());
    }
  }

  public void executePICreationLatencyBenchmark(final CamundaClient camundaClient) {
    ProcessUtil.deployProcess(camundaClient, List.of("bpmn/simpleProcess.bpmn"));

    final var benchmarkResult =
        executeAndMeasure(
            10,
            () -> {
              final var result =
                  ProcessInstanceUtil.startInstance(
                          camundaClient, "simpleProcess", Collections.emptyMap())
                      .join();
              final var pi =
                  getProcessInstance(
                      camundaClient, result.getProcessInstanceKey(), Duration.ofSeconds(10));
            });

    LOG.info(
        "Benchmark result: {} iterations, total {} ms, average {} ms",
        benchmarkResult.iterations,
        benchmarkResult.sumMillis,
        benchmarkResult.averageMillis);
  }

  public BenchmarkResult executeAndMeasure(
      final long iterations, final Runnable benchmarkRunnable) {
    final var resultTimings = new ArrayList<Long>();

    for (long i = 0; i < iterations; i++) {
      final long startTime = System.nanoTime();
      benchmarkRunnable.run();
      final long endTime = System.nanoTime();
      final long durationNanos = endTime - startTime;
      resultTimings.add(durationNanos);
    }

    return new BenchmarkResult(
        iterations,
        resultTimings.stream().mapToLong(Long::longValue).sum() / 1_000_000,
        resultTimings.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000);
  }
  ;

  public static ProcessInstance getProcessInstance(
      final CamundaClient client, final long processInstanceKey, final Duration timeout) {
    final long startTime = System.currentTimeMillis();
    while (startTime + timeout.toMillis() > System.currentTimeMillis()) {
      try {
        return client.newProcessInstanceGetRequest(processInstanceKey).send().join();
      } catch (final Exception e) {
        // ignore and retry
      }
      try {
        Thread.sleep(50);
      } catch (final InterruptedException e) {
        throw new RuntimeException(e);
      }
    }

    throw new RuntimeException(
        "Timed out waiting for process instance " + processInstanceKey + " to be available");
  }

  private CamundaClient createCamundaClient() {
    return newClientBuilder().numJobWorkerExecutionThreads(0).build();
  }

  public static void main(final String[] args) {
    createApp(Benchmark::new);
  }

  public record BenchmarkResult(long iterations, long sumMillis, double averageMillis) {}
}
