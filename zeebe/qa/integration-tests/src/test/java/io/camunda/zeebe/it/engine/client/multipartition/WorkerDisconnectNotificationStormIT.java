/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.engine.client.multipartition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.CamundaFuture;
import io.camunda.client.api.response.ActivateJobsResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerActivateJobsRequest;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
final class WorkerDisconnectNotificationStormIT {
  private static final Logger LOG =
      LoggerFactory.getLogger(WorkerDisconnectNotificationStormIT.class);
  private static final int JOBS = Integer.getInteger("storm.jobs", 200);
  private static final int POLLS = 40;
  private static final int PROBES = 25;
  private static final String ACTIVATION_REQUEST_TYPE =
      new BrokerActivateJobsRequest("unused").getType();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withReplicationFactor(1)
          .withPartitionsCount(3)
          .withGatewaysCount(2)
          .withEmbeddedGateway(false)
          .withBrokerConfig(broker -> broker.withProperty("zeebe.clock.controlled", true))
          .withGatewayConfig(
              gateway ->
                  gateway.withUnifiedConfig(
                      cfg -> {
                        cfg.getApi().getLongPolling().setEnabled(true);
                        cfg.getApi().getLongPolling().setProbeTimeout(120_000);
                      }))
          .build();

  @Test
  void shouldMeasureClientImpactOfOneDisconnectedStreamingWorker(final TestReporter reporter)
      throws Exception {
    // given
    assertThat(JOBS)
        .as("storm.jobs must be positive and divisible by the number of pending polls")
        .isPositive()
        .isGreaterThanOrEqualTo(POLLS);
    assertThat(JOBS % POLLS).isZero();
    final var gateways = List.copyOf(cluster.gateways().values());
    final var broker = cluster.brokers().values().iterator().next();
    final var brokerMetrics = broker.bean(MeterRegistry.class);
    final var gatewayMetrics = gateways.stream().map(g -> g.bean(MeterRegistry.class)).toList();
    final String jobType = "storm-" + UUID.randomUUID();
    final String probeType = "probe-" + UUID.randomUUID();
    final String worker = "disconnected-" + UUID.randomUUID();
    final Set<Long> abandonedKeys = ConcurrentHashMap.newKeySet();
    final Set<Long> recoveredKeys = ConcurrentHashMap.newKeySet();
    final List<Outcome> recovery = new CopyOnWriteArrayList<>();
    final List<Outcome> probes = new CopyOnWriteArrayList<>();
    final List<Outcome> baseline = new CopyOnWriteArrayList<>();
    final List<CamundaFuture<ActivateJobsResponse>> polls = new ArrayList<>();

    try (final var first =
            gateways.getFirst().newClientBuilder().preferRestOverGrpc(false).build();
        final var second =
            gateways.getLast().newClientBuilder().preferRestOverGrpc(false).build()) {
      try (final var failed =
          gateways.getFirst().newClientBuilder().preferRestOverGrpc(false).build()) {
        failed
            .newStreamJobsCommand()
            .jobType(jobType)
            .consumer(job -> abandonedKeys.add(job.getKey()))
            .workerName(worker)
            .timeout(Duration.ofMinutes(30))
            .send();
        await()
            .atMost(Duration.ofSeconds(30))
            .untilAsserted(
                () ->
                    JobStreamActuatorAssert.assertThat(JobStreamActuator.of(broker))
                        .remoteStreams()
                        .haveWorker(1, worker));
        createJobs(first, jobType, JOBS);
        await()
            .atMost(Duration.ofSeconds(60))
            .untilAsserted(() -> assertThat(abandonedKeys).hasSize(JOBS));
      }
      await()
          .atMost(Duration.ofSeconds(30))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(JobStreamActuator.of(broker))
                      .remoteStreams()
                      .doNotHaveWorker(worker));
      createJobs(first, probeType, 2 * PROBES);

      final var clients = List.of(first, second);
      for (int i = 0; i < PROBES; i++) {
        final long started = System.nanoTime();
        final var response =
            clients
                .get(i % clients.size())
                .newActivateJobsCommand()
                .useGrpc()
                .jobType(probeType)
                .maxJobsToActivate(1)
                .timeout(Duration.ofDays(1))
                .requestTimeout(Duration.ofSeconds(2))
                .send()
                .join();
        assertThat(response.getJobs()).hasSize(1);
        baseline.add(new Outcome(elapsedMs(started), null));
      }
      for (int i = 0; i < POLLS; i++) {
        final var started = System.nanoTime();
        final var poll =
            clients
                .get(i % clients.size())
                .newActivateJobsCommand()
                .useGrpc()
                .jobType(jobType)
                .maxJobsToActivate(Math.max(1, JOBS / POLLS))
                .workerName("survivor-" + i)
                .timeout(Duration.ofDays(1))
                .requestTimeout(Duration.ofSeconds(30))
                .send();
        polls.add(poll);
        poll.whenComplete(
            (response, error) -> {
              if (response != null) {
                response.getJobs().forEach(job -> recoveredKeys.add(job.getKey()));
              }
              recovery.add(new Outcome(elapsedMs(started), error));
            });
      }
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () -> {
                for (final var registry : gatewayMetrics) {
                  assertThat(
                          registry
                              .find("zeebe.long.polling.queued.current")
                              .tags("type", jobType, "protocol", "grpc")
                              .gauges()
                              .stream()
                              .mapToDouble(gauge -> gauge.value())
                              .sum())
                      .isEqualTo(POLLS / 2);
                }
              });
      final double notificationsBefore = jobEvents(brokerMetrics, jobType, "workers notified");
      final double wireBefore = wireMessages(brokerMetrics);
      final double requestsBefore =
          gatewayMetrics.stream()
              .mapToDouble(WorkerDisconnectNotificationStormIT::activationRequests)
              .sum();

      // when
      try (final var probeExecutor = Executors.newSingleThreadScheduledExecutor()) {
        final List<CompletableFuture<?>> probeResults = new ArrayList<>();
        for (int i = 0; i < PROBES; i++) {
          final var finished = new CompletableFuture<Void>();
          probeResults.add(finished);
          final var client = clients.get(i % clients.size());
          probeExecutor.schedule(
              () -> {
                final long started = System.nanoTime();
                client
                    .newActivateJobsCommand()
                    .useGrpc()
                    .jobType(probeType)
                    .maxJobsToActivate(1)
                    .timeout(Duration.ofDays(1))
                    .requestTimeout(Duration.ofSeconds(2))
                    .send()
                    .whenComplete(
                        (response, error) -> {
                          final var outcomeError =
                              error != null
                                  ? error
                                  : response.getJobs().size() == 1
                                      ? null
                                      : new AssertionError("Probe returned no job");
                          probes.add(new Outcome(elapsedMs(started), outcomeError));
                          finished.complete(null);
                        });
              },
              i * 50L,
              TimeUnit.MILLISECONDS);
        }
        ActorClockActuator.of(broker)
            .addTime(new AddTimeRequest(Duration.ofMinutes(31).toMillis()));
        CompletableFuture.allOf(probeResults.toArray(CompletableFuture[]::new))
            .get(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(
                polls.stream()
                    .map(p -> p.handle((r, e) -> null).toCompletableFuture())
                    .toArray(CompletableFuture[]::new))
            .get(45, TimeUnit.SECONDS);
      }

      // then
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(() -> assertThat(recovery).hasSize(POLLS));
      final double notified =
          jobEvents(brokerMetrics, jobType, "workers notified") - notificationsBefore;
      final double wire = wireMessages(brokerMetrics) - wireBefore;
      final double activations =
          gatewayMetrics.stream()
                  .mapToDouble(WorkerDisconnectNotificationStormIT::activationRequests)
                  .sum()
              - requestsBefore;
      final var results = new LinkedHashMap<String, String>();
      results.put("jobs", String.valueOf(JOBS));
      results.put("timedOut", String.valueOf(jobEvents(brokerMetrics, jobType, "timed out")));
      results.put("pushFailed", String.valueOf(jobEvents(brokerMetrics, jobType, "push fail")));
      results.put("notifications", String.valueOf(notified));
      results.put("wireMessages", String.valueOf(wire));
      results.put("brokerActivationsIncludingProbes", String.valueOf(activations));
      results.put("firstWaveRecovered", String.valueOf(recoveredKeys.size()));
      results.put("recoveryErrors", String.valueOf(errors(recovery)));
      results.put("probeErrors", String.valueOf(errors(probes)));
      results.put("baselineP50Ms", String.valueOf(percentile(baseline, 0.50)));
      results.put("baselineP99Ms", String.valueOf(percentile(baseline, 0.99)));
      results.put("probeP50Ms", String.valueOf(percentile(probes, 0.50)));
      results.put("probeP99Ms", String.valueOf(percentile(probes, 0.99)));
      reporter.publishEntry(results);
      logReport(results);
      assertThat(jobEvents(brokerMetrics, jobType, "timed out")).isEqualTo(JOBS);
      assertThat(notified).isPositive();
      assertThat(wire).isPositive();
      assertThat(activations).isPositive();
      // ActivateJobs may return a partially filled batch; drain the remaining jobs with new polls.
      final long recoveryDeadline = System.nanoTime() + Duration.ofSeconds(30).toNanos();
      while (recoveredKeys.size() < JOBS && System.nanoTime() < recoveryDeadline) {
        final var remaining =
            first
                .newActivateJobsCommand()
                .useGrpc()
                .jobType(jobType)
                .maxJobsToActivate(JOBS - recoveredKeys.size())
                .timeout(Duration.ofDays(1))
                .requestTimeout(Duration.ofSeconds(2))
                .send()
                .join();
        remaining.getJobs().forEach(job -> recoveredKeys.add(job.getKey()));
      }
      assertThat(recoveredKeys.size()).as("all abandoned jobs recovered").isEqualTo(JOBS);
      assertThat(recoveredKeys.equals(abandonedKeys))
          .as("recovered keys match abandoned keys")
          .isTrue();
      assertThat(errors(recovery)).isZero();
      assertThat(probes).hasSize(PROBES);
      probes.stream()
          .filter(o -> o.error() != null)
          .forEach(o -> LOG.warn("Probe error", o.error()));
      assertThat(errors(probes)).isZero();
    } finally {
      polls.forEach(p -> p.cancel(true));
    }
  }

  private static void logReport(final Map<String, String> results) {
    final var report = new StringBuilder("\n");
    report.append("================ WORKER_DISCONNECT_RESULT ================\n");
    report.append("Scenario: Streaming worker disconnect with pending gRPC recovery polls\n");
    report.append("Topology: 1 broker / 3 partitions / 2 standalone gateways\n");
    report
        .append("Recovery polls: ")
        .append(POLLS)
        .append("; probes per phase: ")
        .append(PROBES)
        .append("\n\n");
    results.forEach(
        (key, value) -> {
          final String label =
              switch (key) {
                case "jobs" -> "Jobs abandoned by disconnected worker";
                case "timedOut" -> "Jobs timed out";
                case "pushFailed" -> "Failed stream pushes";
                case "notifications" -> "Availability notifications emitted (delta)";
                case "wireMessages" -> "Broker -> gateway messages (delta)";
                case "brokerActivationsIncludingProbes" ->
                    "Gateway -> broker activations (incl. probes)";
                case "firstWaveRecovered" -> "Jobs recovered in first polling wave";
                case "recoveryErrors" -> "Recovery request errors";
                case "probeErrors" -> "Recovery-phase probe errors";
                case "baselineP50Ms" -> "Baseline probe latency p50 (ms)";
                case "baselineP99Ms" -> "Baseline probe latency p99 (ms)";
                case "probeP50Ms" -> "Recovery probe latency p50 (ms)";
                case "probeP99Ms" -> "Recovery probe latency p99 (ms)";
                default -> key;
              };
          report.append(String.format(Locale.ROOT, "  %-44s : %s%n", label, value));
        });
    final double notifications = Double.parseDouble(results.get("notifications"));
    final double messages = Double.parseDouble(results.get("wireMessages"));
    final String amplification =
        notifications > 0
            ? String.format(Locale.ROOT, "%.2fx", messages / notifications)
            : "n/a (no notifications)";
    report.append("\nObserved message amplification: ").append(amplification).append('\n');
    report.append("Fan-out model: 2 compatibility topics x 2 gateway subscribers = 4x\n");
    report.append("Scope: recovery traffic and client latency; CPU saturation is not measured.\n");
    report.append("================ END WORKER_DISCONNECT_RESULT ================");
    LOG.info("{}", report);
  }

  private static void createJobs(final CamundaClient client, final String type, final int count) {
    final var model =
        Bpmn.createExecutableProcess(type)
            .startEvent()
            .serviceTask(
                "task",
                t ->
                    t.zeebeJobType(type)
                        .multiInstance(
                            mi ->
                                mi.parallel()
                                    .zeebeInputCollectionExpression(
                                        "for i in 1.." + count + " return i")
                                    .zeebeInputElement("item")))
            .endEvent()
            .done();
    final var deployment =
        client.newDeployResourceCommand().addProcessModel(model, type + ".bpmn").send().join();
    client
        .newCreateInstanceCommand()
        .processDefinitionKey(deployment.getProcesses().getFirst().getProcessDefinitionKey())
        .send()
        .join();
  }

  private static double jobEvents(
      final MeterRegistry registry, final String type, final String action) {
    return registry
        .find("zeebe.job.events.total")
        .tags("type", type, "action", action)
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private static double wireMessages(final MeterRegistry registry) {
    return registry.find("zeebe.messaging.request.count").counters().stream()
        .filter(c -> String.valueOf(c.getId().getTag("topic")).endsWith("jobsAvailable"))
        .mapToDouble(Counter::count)
        .sum();
  }

  private static double activationRequests(final MeterRegistry registry) {
    return registry.find("zeebe.gateway.total.requests").counters().stream()
        .filter(c -> ACTIVATION_REQUEST_TYPE.equals(c.getId().getTag("requestType")))
        .mapToDouble(Counter::count)
        .sum();
  }

  private static long errors(final List<Outcome> outcomes) {
    return outcomes.stream().filter(o -> o.error() != null).count();
  }

  private static long elapsedMs(final long started) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
  }

  private static long percentile(final List<Outcome> outcomes, final double percentile) {
    final var sorted = outcomes.stream().mapToLong(Outcome::millis).sorted().toArray();
    return sorted[Math.min(sorted.length - 1, (int) Math.ceil(sorted.length * percentile) - 1)];
  }

  private record Outcome(long millis, Throwable error) {}
}
