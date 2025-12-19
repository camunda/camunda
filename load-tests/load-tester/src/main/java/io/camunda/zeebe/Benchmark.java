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
      case "measure-cpu-usage" -> measureCPUUsage();
      case "maximize-load" -> maximizeLoad();
      case "none" -> {}
      default -> throw new IllegalArgumentException("Unknown benchmark: " + benchmarkCfg.getName());
    }
  }

  public void executePICreationLatencyBenchmark(final CamundaClient camundaClient) {
    ProcessUtil.deployProcess(camundaClient, List.of("bpmn/simpleProcess.bpmn"));

    final var duration = benchmarkCfg.getDuration();
    final var benchmarkResult =
        executeAndMeasureForDuration(
            duration,
            () -> {
              final var result =
                  ProcessInstanceUtil.startInstance(
                          camundaClient, "simpleProcess", Collections.emptyMap(), null)
                      .toCompletableFuture()
                      .join();
              final var pi =
                  getProcessInstance(
                      camundaClient, result.getProcessInstanceKey(), Duration.ofSeconds(10));
            });

    LOG.info(
        "Benchmark result over {} seconds: {} iterations, total {} ms, average {} ms",
        duration.toSeconds(),
        benchmarkResult.iterations,
        benchmarkResult.sumMillis,
        benchmarkResult.averageMillis);
  }

  public void measureCPUUsage() {
    final var duration = benchmarkCfg.getDuration();
    final var metricsReader = createMetricsReader();

    final List<Double> cpuUsages = new ArrayList<>();
    final long start = System.currentTimeMillis();
    while (System.currentTimeMillis() - start < duration.toMillis()) {
      try {
        final var cpuUsage = metricsReader.getCurrentCpuLoad();
        LOG.info("Current CPU usage: {}", cpuUsage);
        cpuUsages.add(cpuUsage);
        Thread.sleep(1000);
      } catch (final Exception e) {
        THROTTLED_LOGGER.warn("Failed to read CPU usage from Prometheus", e);
      }
    }

    final double averageCpuUsage =
        cpuUsages.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    LOG.info("Average CPU usage over {} seconds: {}", duration.toSeconds(), averageCpuUsage);
  }

  public void maximizeLoad() {
    final var duration = benchmarkCfg.getDuration();
    final var metricsReader = createMetricsReader();

    final var createdProcessInstancesBefore = metricsReader.getTotalCreatedProcessInstances();
    LOG.info("Created process instances before: {}", createdProcessInstancesBefore);
    try {
      Thread.sleep(duration.toMillis());
    } catch (final InterruptedException e) {
      LOG.warn("Got interrupted", e);
    }

    final var createdProcessInstancesAfter = metricsReader.getTotalCreatedProcessInstances();
    LOG.info("Created process instances after: {}", createdProcessInstancesAfter);

    final double averageProcessInstancesPerSecond =
        (createdProcessInstancesAfter - createdProcessInstancesBefore)
            / (double) duration.toSeconds();
    LOG.info(
        "Average created process instances per second over {} seconds: {}",
        duration.toSeconds(),
        averageProcessInstancesPerSecond);
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

  public BenchmarkResult executeAndMeasureForDuration(
      final Duration duration, final Runnable benchmarkRunnable) {
    final var resultTimings = new ArrayList<Long>();
    final long startTime = System.currentTimeMillis();
    long iterations = 0;

    while (System.currentTimeMillis() - startTime < duration.toMillis()) {
      final long iterationStartTime = System.nanoTime();
      benchmarkRunnable.run();
      final long iterationEndTime = System.nanoTime();
      final long durationNanos = iterationEndTime - iterationStartTime;
      resultTimings.add(durationNanos);
      iterations++;
    }

    return new BenchmarkResult(
        iterations,
        resultTimings.stream().mapToLong(Long::longValue).sum() / 1_000_000,
        resultTimings.stream().mapToLong(Long::longValue).average().orElse(0.0) / 1_000_000);
  }

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
