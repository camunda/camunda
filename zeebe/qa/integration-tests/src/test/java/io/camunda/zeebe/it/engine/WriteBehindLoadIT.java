/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * On-demand load test: measures how long a single broker with one partition needs to start and
 * complete N instances of a one-service-task process, served by an in-process job worker. Run it
 * with plain RocksDB state and with the write-behind state DB to compare them. No exporter and no
 * secondary storage is configured, so only the engine and its state are measured.
 *
 * <p>It is skipped unless the environment variable {@code WRITE_BEHIND_LOAD_TEST=true} is set. The
 * {@code --rerun} and {@code --no-configuration-cache} flags are needed because Gradle does not
 * track environment variables: it would otherwise treat the task as up to date or reuse the
 * environment of an earlier run.
 *
 * <pre>
 * WRITE_BEHIND_LOAD_TEST=true LOAD_WRITE_BEHIND=false ./gradlew :zeebe-qa-integration-tests:it \
 *     --tests '*WriteBehindLoadIT' --rerun --no-configuration-cache -i
 * WRITE_BEHIND_LOAD_TEST=true LOAD_WRITE_BEHIND=true ./gradlew :zeebe-qa-integration-tests:it \
 *     --tests '*WriteBehindLoadIT' --rerun --no-configuration-cache -i
 * </pre>
 *
 * Optional environment variables (defaults in brackets): {@code LOAD_WRITE_BEHIND} [false], {@code
 * LOAD_INSTANCES} [100000], {@code LOAD_WARMUP} [10% of the instances], {@code LOAD_IN_FLIGHT}
 * [256] maximum concurrent create commands, {@code LOAD_MAX_JOBS_ACTIVE} [256]. The summary is
 * printed on one line starting with {@code LOAD-TEST-RESULT}.
 */
@ZeebeIntegration
@EnabledIfEnvironmentVariable(named = "WRITE_BEHIND_LOAD_TEST", matches = "true")
final class WriteBehindLoadIT {

  private static final boolean WRITE_BEHIND =
      Boolean.parseBoolean(env("LOAD_WRITE_BEHIND", "false"));
  private static final int INSTANCES = Integer.parseInt(env("LOAD_INSTANCES", "100000"));
  private static final int WARMUP = Integer.parseInt(env("LOAD_WARMUP", "" + INSTANCES / 10));
  private static final int IN_FLIGHT = Integer.parseInt(env("LOAD_IN_FLIGHT", "256"));
  private static final int MAX_JOBS_ACTIVE = Integer.parseInt(env("LOAD_MAX_JOBS_ACTIVE", "256"));

  @TestZeebe
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withProperty("zeebe.broker.cluster.partitionsCount", 1)
          .withProperty("zeebe.broker.experimental.writeBehind.enabled", WRITE_BEHIND);

  @AutoClose private final CamundaClient client = BROKER.newClientBuilder().build();
  private final AtomicReference<Throwable> firstFailure = new AtomicReference<>();

  @Test
  void shouldCompleteInstances() {
    // given
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("load")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("load-task"))
                .endEvent()
                .done(),
            "load.bpmn")
        .send()
        .join();
    final var completedJobs = new AtomicLong();

    // a job is completed right before its instance ends, so counting completed jobs is a good
    // proxy for completed instances and, unlike withResult() creates, does not make the creation
    // path wait for the whole instance
    try (final var worker =
        client
            .newWorker()
            .jobType("load-task")
            .handler((jobClient, job) -> complete(jobClient, job, completedJobs))
            .maxJobsActive(MAX_JOBS_ACTIVE)
            .streamEnabled(true)
            .open()) {
      runBatch(WARMUP, completedJobs);

      // when
      final var start = System.nanoTime();
      runBatch(INSTANCES, completedJobs);
      final var elapsed = Duration.ofNanos(System.nanoTime() - start);

      // then
      final var seconds = elapsed.toNanos() / 1e9;
      System.out.printf(
          "LOAD-TEST-RESULT mode=%s instances=%d duration=%.2fs throughput=%.1f instances/s%n",
          WRITE_BEHIND ? "write-behind" : "rocksdb", INSTANCES, seconds, INSTANCES / seconds);
      assertThat(completedJobs.get()).isEqualTo(WARMUP + INSTANCES);
      assertThat(writeBehindThreadExists()).isEqualTo(WRITE_BEHIND);
    }
  }

  // asynchronous: a blocking join here would serialize all completions on the worker's single
  // handler thread, which then limits the throughput instead of the broker
  private void complete(
      final JobClient jobClient, final ActivatedJob job, final AtomicLong completedJobs) {
    jobClient
        .newCompleteCommand(job)
        .send()
        .whenComplete(
            (r, t) -> {
              if (t == null) {
                completedJobs.incrementAndGet();
              } else if (isBackpressure(t)) {
                complete(jobClient, job, completedJobs);
              } else {
                firstFailure.compareAndSet(null, t);
              }
            });
  }

  private void runBatch(final int count, final AtomicLong completedJobs) {
    final var target = completedJobs.get() + count;
    final var inFlight = new Semaphore(IN_FLIGHT);
    for (int i = 0; i < count; i++) {
      inFlight.acquireUninterruptibly();
      create(inFlight);
    }
    Awaitility.await("all instances are completed")
        .atMost(Duration.ofMinutes(30))
        .pollInterval(Duration.ofMillis(50))
        .until(() -> firstFailure.get() != null || completedJobs.get() >= target);
    assertThat(firstFailure.get()).as("failed command").isNull();
  }

  // the broker answers RESOURCE_EXHAUSTED when its backpressure limit is hit; that is expected
  // under load, so the command is sent again instead of failing the run
  private void create(final Semaphore inFlight) {
    client
        .newCreateInstanceCommand()
        .bpmnProcessId("load")
        .latestVersion()
        .send()
        .whenComplete(
            (r, t) -> {
              if (t != null && isBackpressure(t)) {
                create(inFlight);
                return;
              }
              if (t != null) {
                firstFailure.compareAndSet(null, t);
              }
              inFlight.release();
            });
  }

  private static boolean isBackpressure(final Throwable t) {
    return t.toString().contains("RESOURCE_EXHAUSTED");
  }

  private static boolean writeBehindThreadExists() {
    return Thread.getAllStackTraces().keySet().stream()
        .anyMatch(t -> t.getName().equals("zb-db-write-behind"));
  }

  private static String env(final String name, final String defaultValue) {
    final var value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }
}
