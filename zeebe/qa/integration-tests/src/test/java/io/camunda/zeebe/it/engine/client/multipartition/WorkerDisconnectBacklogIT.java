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

import io.camunda.client.api.worker.JobWorker;
import io.camunda.zeebe.broker.Broker;
import io.camunda.zeebe.it.util.ZeebeResourcesHelper;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator;
import io.camunda.zeebe.qa.util.actuator.ActorClockActuator.AddTimeRequest;
import io.camunda.zeebe.qa.util.actuator.JobStreamActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.jobstream.JobStreamActuatorAssert;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
final class WorkerDisconnectBacklogIT {
  private static final int JOBS = Integer.getInteger("storm.backlogJobs", 1000);
  private static final int REQUESTS = 10;
  private final List<JobWorker> workers = new ArrayList<>();

  @TestZeebe
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(1)
          .withPartitionsCount(1)
          .withReplicationFactor(1)
          .withGatewaysCount(3)
          .withEmbeddedGateway(false)
          .useRecordingExporter(true)
          .withBrokerConfig(
              broker ->
                  broker.withUnifiedConfig(
                      cfg -> {
                        cfg.getSystem().setClockControlled(true);
                        cfg.getSystem().setCpuThreadCount(4);
                      }))
          .build();

  @Test
  void shouldRecoverJobsActivatedBeforeAndAfterWorkerDisconnect(final TestReporter reporter)
      throws Exception {
    // given
    assertThat(JOBS % REQUESTS).isZero();
    assertThat(JOBS).isPositive();
    final var broker = cluster.brokers().values().iterator().next();
    final var gateways = List.copyOf(cluster.gateways().values());
    final String type = "backlog-" + UUID.randomUUID();
    final String deadWorker = "dead-" + UUID.randomUUID();
    final var registry = broker.bean(MeterRegistry.class);
    final Set<Long> completed = ConcurrentHashMap.newKeySet();
    final List<Throwable> completionErrors = new CopyOnWriteArrayList<>();
    final var release = new CountDownLatch(1);
    final var entered = new CountDownLatch(1);
    try (final var producer = gateways.get(0).newClientBuilder().preferRestOverGrpc(false).build();
        final var failed = gateways.get(0).newClientBuilder().preferRestOverGrpc(false).build();
        final var survivorA = gateways.get(1).newClientBuilder().preferRestOverGrpc(false).build();
        final var survivorB = gateways.get(2).newClientBuilder().preferRestOverGrpc(false).build();
        final AutoCloseable cleanup =
            () -> {
              release.countDown();
              workers.forEach(JobWorker::close);
              workers.clear();
            }) {
      final var resources = new ZeebeResourcesHelper(producer);
      final var heldKeys = resources.createJobs(type, JOBS);
      final var held =
          failed
              .newActivateJobsCommand()
              .useGrpc()
              .jobType(type)
              .maxJobsToActivate(JOBS)
              .workerName(deadWorker)
              .timeout(Duration.ofMinutes(30))
              .send()
              .join();
      assertThat(held.getJobs()).hasSize(JOBS);
      final var backlogKeys = resources.createJobs(type, JOBS);
      final var processor =
          broker
              .bean(Broker.class)
              .getBrokerContext()
              .getPartitionManager()
              .getZeebePartitions()
              .iterator()
              .next()
              .getStreamProcessor()
              .get(5, TimeUnit.SECONDS)
              .orElseThrow();
      final long beforePosition =
          processor.getLastProcessedPositionAsync().get(5, TimeUnit.SECONDS);
      final double receivedBefore = counter(registry, "zeebe.received.request.count.total");
      final double notifiedBefore =
          counter(registry, "zeebe.job.events.total", "type", type, "action", "workers notified");
      final double wireBefore = notificationMessages(registry);
      final var gatewayRegistries =
          gateways.stream().map(g -> g.bean(MeterRegistry.class)).toList();
      final double activationsBefore = activationRequests(gatewayRegistries);

      // Hold processing, not request admission: the management pause would reject new commands.
      final var gate =
          processor.call(
              () -> {
                entered.countDown();
                if (!release.await(30, TimeUnit.SECONDS)) {
                  throw new IllegalStateException("Timed out waiting to release backlog processor");
                }
                return null;
              });
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        for (int i = 0; i < REQUESTS; i++) {
          failed
              .newActivateJobsCommand()
              .useGrpc()
              .jobType(type)
              .maxJobsToActivate(JOBS / REQUESTS)
              .workerName(deadWorker)
              .timeout(Duration.ofMinutes(30))
              .requestTimeout(Duration.ofMinutes(1))
              .send();
        }
        await()
            .atMost(Duration.ofSeconds(10))
            .untilAsserted(
                () ->
                    assertThat(
                            counter(registry, "zeebe.received.request.count.total")
                                - receivedBefore)
                        .as("activation requests received by broker while processing is held")
                        .isGreaterThanOrEqualTo(REQUESTS));

        // when
        failed.close();
      } finally {
        release.countDown();
      }
      gate.get(5, TimeUnit.SECONDS);
      final var lateActivations =
          RecordingExporter.jobBatchRecords(JobBatchIntent.ACTIVATED)
              .withType(type)
              .filter(r -> r.getPosition() > beforePosition)
              .filter(r -> r.getValue().getWorker().equals(deadWorker))
              .limit(REQUESTS)
              .toList();
      final Set<Long> lateKeys = new HashSet<>();
      lateActivations.forEach(r -> lateKeys.addAll(r.getValue().getJobKeys()));
      assertThat(lateKeys).containsExactlyInAnyOrderElementsOf(backlogKeys);

      final var failedDeliveries =
          RecordingExporter.jobRecords(JobIntent.FAILED)
              .withType(type)
              .filter(r -> backlogKeys.contains(r.getKey()))
              .limit(JOBS)
              .toList();
      assertThat(failedDeliveries).hasSize(JOBS);

      for (final var client : List.of(survivorA, survivorB)) {
        workers.add(
            client
                .newWorker()
                .jobType(type)
                .handler(
                    (jobClient, job) ->
                        jobClient
                            .newCompleteCommand(job)
                            .send()
                            .whenComplete(
                                (r, error) -> {
                                  if (error == null) {
                                    completed.add(job.getKey());
                                  } else {
                                    completionErrors.add(error);
                                  }
                                }))
                .name("survivor-" + UUID.randomUUID())
                .streamEnabled(true)
                .maxJobsActive(32)
                .pollInterval(Duration.ofMillis(100))
                .timeout(Duration.ofDays(1))
                .open());
      }
      final long recoveryStarted = System.nanoTime();
      ActorClockActuator.of(broker).addTime(new AddTimeRequest(Duration.ofMinutes(31).toMillis()));
      await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(() -> assertThat(completed).hasSize(2 * JOBS));

      // then
      final var timedOut =
          RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withType(type).limit(JOBS).toList();
      assertThat(timedOut)
          .extracting(r -> r.getKey())
          .containsExactlyInAnyOrderElementsOf(heldKeys);
      final var results = new LinkedHashMap<String, String>();
      results.put("heldBeforeDisconnect", String.valueOf(heldKeys.size()));
      results.put("activatedAfterDisconnect", String.valueOf(lateKeys.size()));
      results.put("failedPullDeliveries", String.valueOf(failedDeliveries.size()));
      results.put(
          "timedOut",
          String.valueOf(
              counter(registry, "zeebe.job.events.total", "type", type, "action", "timed out")));
      results.put(
          "pushFailed", String.valueOf(counter(registry, "zeebe.broker.jobs.push.fail.count")));
      results.put(
          "gatewayBrokerActivations",
          String.valueOf(activationRequests(gatewayRegistries) - activationsBefore));
      results.put("yieldedRecords", String.valueOf(jobEventCount(type, JobIntent.YIELDED)));
      results.put(
          "notifications",
          String.valueOf(
              counter(
                      registry,
                      "zeebe.job.events.total",
                      "type",
                      type,
                      "action",
                      "workers notified")
                  - notifiedBefore));
      results.put("wireMessages", String.valueOf(notificationMessages(registry) - wireBefore));
      results.put(
          "gatewayBrokerActivationsByNode",
          gateways.stream()
              .map(
                  g ->
                      g.nodeId()
                          + "="
                          + counter(
                              g.bean(MeterRegistry.class),
                              "zeebe.gateway.total.requests",
                              "requestType",
                              "JOB_BATCH#ACTIVATE"))
              .toList()
              .toString());
      results.put("completed", String.valueOf(completed.size()));
      results.put("completionErrors", String.valueOf(completionErrors.size()));
      results.put(
          "recoveryMs",
          String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - recoveryStarted)));
      reporter.publishEntry(results);
      logReport(
          "WORKER_BACKLOG_RESULT", "Queued pull activations after worker disconnect", results);
      assertThat(completionErrors).isEmpty();
      assertThat(completed).containsAll(heldKeys).containsAll(backlogKeys);
      assertFanOut(registry, type, notifiedBefore, wireBefore);
      workers.forEach(JobWorker::close);
      workers.clear();
    } finally {
      release.countDown();
      workers.forEach(JobWorker::close);
    }
  }

  @Test
  void shouldAmplifyTimeoutAndYieldNotificationsWhenStreamingWorkerStopsReading(
      final TestReporter reporter) throws Exception {
    // given
    final var broker = cluster.brokers().values().iterator().next();
    final var gateways = List.copyOf(cluster.gateways().values());
    final var registry = broker.bean(MeterRegistry.class);
    final String type = "stream-backlog-" + UUID.randomUUID();
    final String worker = "stalled-" + UUID.randomUUID();
    final var stoppedReading = new CountDownLatch(1);
    final var releaseConsumer = new CountDownLatch(1);
    final Set<Long> received = ConcurrentHashMap.newKeySet();
    final Set<Long> completed = ConcurrentHashMap.newKeySet();
    final List<Throwable> errors = new CopyOnWriteArrayList<>();
    try (final var producer = gateways.get(0).newClientBuilder().preferRestOverGrpc(false).build();
        final var failed = gateways.get(0).newClientBuilder().preferRestOverGrpc(false).build();
        final var first = gateways.get(1).newClientBuilder().preferRestOverGrpc(false).build();
        final var second = gateways.get(2).newClientBuilder().preferRestOverGrpc(false).build();
        final AutoCloseable cleanup =
            () -> {
              releaseConsumer.countDown();
              workers.forEach(JobWorker::close);
              workers.clear();
            }) {
      final var stream =
          failed
              .newStreamJobsCommand()
              .jobType(type)
              .consumer(
                  job -> {
                    received.add(job.getKey());
                    stoppedReading.countDown();
                    try {
                      if (!releaseConsumer.await(30, TimeUnit.SECONDS)) {
                        throw new IllegalStateException(
                            "Timed out waiting to release stalled stream consumer");
                      }
                    } catch (final InterruptedException e) {
                      Thread.currentThread().interrupt();
                      throw new IllegalStateException(e);
                    }
                  })
              .workerName(worker)
              .timeout(Duration.ofMinutes(30))
              .send();
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(JobStreamActuator.of(broker))
                      .remoteStreams()
                      .haveWorker(1, worker));
      final double beforeWire = notificationMessages(registry);
      final double beforePushFailures = counter(registry, "zeebe.broker.jobs.push.fail.count");
      final var gatewayRegistries =
          gateways.stream().map(g -> g.bean(MeterRegistry.class)).toList();
      final double beforeActivations = activationRequests(gatewayRegistries);
      final double beforeNotify =
          counter(registry, "zeebe.job.events.total", "type", type, "action", "workers notified");
      final var resources = new ZeebeResourcesHelper(producer);
      // A payload above the gRPC readiness threshold fills transport buffers when the consumer
      // stops.
      final var jobKeys =
          resources.createJobs(
              type, task -> {}, "{\"payload\":\"" + "x".repeat(128 * 1024) + "\"}", JOBS);
      assertThat(stoppedReading.await(5, TimeUnit.SECONDS)).isTrue();
      final var yielded =
          RecordingExporter.jobRecords(JobIntent.YIELDED).withType(type).limit(1).getFirst();
      assertThat(jobKeys).contains(yielded.getKey());

      // when
      stream.cancel(true);
      releaseConsumer.countDown();
      failed.close();
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(JobStreamActuator.of(broker))
                      .remoteStreams()
                      .doNotHaveWorker(worker));
      for (final var client : List.of(first, second)) {
        workers.add(
            client
                .newWorker()
                .jobType(type)
                .handler(
                    (c, job) ->
                        c.newCompleteCommand(job)
                            .send()
                            .whenComplete(
                                (r, error) -> {
                                  if (error == null) {
                                    completed.add(job.getKey());
                                  } else {
                                    errors.add(error);
                                  }
                                }))
                .name("survivor-" + UUID.randomUUID())
                .streamEnabled(true)
                .fetchVariables(List.of())
                .maxJobsActive(32)
                .pollInterval(Duration.ofMillis(100))
                .timeout(Duration.ofDays(1))
                .open());
      }
      await()
          .atMost(Duration.ofSeconds(10))
          .untilAsserted(
              () ->
                  JobStreamActuatorAssert.assertThat(JobStreamActuator.of(broker))
                      .remoteStreams()
                      .haveJobType(2, type));
      final long started = System.nanoTime();
      ActorClockActuator.of(broker).addTime(new AddTimeRequest(Duration.ofMinutes(31).toMillis()));
      // New work keeps both surviving streams busy while abandoned activations are being recovered.
      final var newKeys = resources.createJobs(type, JOBS);
      await()
          .atMost(Duration.ofSeconds(60))
          .untilAsserted(() -> assertThat(completed).hasSize(2 * JOBS));

      // then
      final var timedOut =
          RecordingExporter.jobRecords(JobIntent.TIMED_OUT).withType(type).limit(1).getFirst();
      assertThat(jobKeys).contains(timedOut.getKey());
      RecordingExporter.jobRecords(JobIntent.COMPLETED).withType(type).limit(2L * JOBS).await();
      final var results = new LinkedHashMap<String, String>();
      results.put("backlogJobs", String.valueOf(jobKeys.size()));
      results.put("receivedBeforeDisconnect", String.valueOf(received.size()));
      results.put("newWorkDuringRecovery", String.valueOf(newKeys.size()));
      results.put(
          "timedOut",
          String.valueOf(
              counter(registry, "zeebe.job.events.total", "type", type, "action", "timed out")));
      results.put(
          "pushFailed",
          String.valueOf(
              counter(registry, "zeebe.broker.jobs.push.fail.count") - beforePushFailures));
      results.put("yieldedRecords", String.valueOf(jobEventCount(type, JobIntent.YIELDED)));
      results.put(
          "gatewayBrokerActivations",
          String.valueOf(activationRequests(gatewayRegistries) - beforeActivations));
      results.put(
          "notifications",
          String.valueOf(
              counter(
                      registry,
                      "zeebe.job.events.total",
                      "type",
                      type,
                      "action",
                      "workers notified")
                  - beforeNotify));
      results.put("wireMessages", String.valueOf(notificationMessages(registry) - beforeWire));
      results.put(
          "gatewayBrokerActivationsByNode",
          gateways.stream()
              .map(
                  g ->
                      g.nodeId()
                          + "="
                          + counter(
                              g.bean(MeterRegistry.class),
                              "zeebe.gateway.total.requests",
                              "requestType",
                              "JOB_BATCH#ACTIVATE"))
              .toList()
              .toString());
      results.put("completed", String.valueOf(completed.size()));
      results.put("completionErrors", String.valueOf(errors.size()));
      results.put(
          "recoveryMs", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)));
      reporter.publishEntry(results);
      logReport(
          "STREAM_BACKLOG_RESULT", "Blocked job stream followed by worker disconnect", results);
      assertThat(errors).isEmpty();
      assertThat(completed).containsAll(jobKeys).containsAll(newKeys);
      assertThat(jobEventCount(type, JobIntent.YIELDED)).isPositive();
      assertThat(counter(registry, "zeebe.broker.jobs.push.fail.count") - beforePushFailures)
          .isPositive();
      assertFanOut(registry, type, beforeNotify, beforeWire);
      workers.forEach(JobWorker::close);
      workers.clear();
    } finally {
      releaseConsumer.countDown();
      workers.forEach(JobWorker::close);
    }
  }

  private static void logReport(
      final String marker, final String scenario, final Map<String, String> results) {
    final var report = new StringBuilder("\n");
    report.append("================ ").append(marker).append(" ================\n");
    report.append("Scenario: ").append(scenario).append('\n');
    report.append("Topology: 1 broker / 1 partition / 3 standalone gateways\n\n");
    results.forEach(
        (key, value) -> {
          final String label =
              switch (key) {
                case "heldBeforeDisconnect" -> "Jobs held before disconnect";
                case "activatedAfterDisconnect" -> "Jobs activated AFTER disconnect";
                case "failedPullDeliveries" -> "Failed pull deliveries (FAILED records)";
                case "backlogJobs" -> "Jobs in streaming backlog";
                case "receivedBeforeDisconnect" -> "Jobs received before disconnect";
                case "newWorkDuringRecovery" -> "New jobs submitted during recovery";
                case "timedOut" -> "Jobs timed out";
                case "pushFailed" -> "Failed stream pushes";
                case "yieldedRecords" -> "YIELDED records";
                case "notifications" -> "Availability notifications emitted (delta)";
                case "wireMessages" -> "Broker -> gateway messages (delta)";
                case "gatewayBrokerActivations" -> "Gateway -> broker activations (delta)";
                case "gatewayBrokerActivationsByNode" -> "Activations per gateway (cumulative)";
                case "completed" -> "Jobs completed";
                case "completionErrors" -> "Job completion errors";
                case "recoveryMs" -> "Recovery duration (ms)";
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
    report.append("Fan-out model: 2 compatibility topics x 3 gateway subscribers = 6x\n");
    report.append(
        "Scope: notification amplification and recovery; CPU saturation is not measured.\n");
    report.append("================ END ").append(marker).append(" ================");
    LoggerFactory.getLogger(WorkerDisconnectBacklogIT.class).info("{}", report);
  }

  private static void assertFanOut(
      final MeterRegistry registry,
      final String type,
      final double beforeNotify,
      final double beforeWire) {
    final double notified =
        counter(registry, "zeebe.job.events.total", "type", type, "action", "workers notified")
            - beforeNotify;
    assertThat(notified).isPositive();
    // Default tenant: two compatibility topics delivered to three standalone gateways.
    await()
        .untilAsserted(
            () ->
                assertThat(notificationMessages(registry) - beforeWire)
                    .as("two topics times three remote subscribers per emitted notification")
                    .isEqualTo(6 * notified));
  }

  private static long jobEventCount(final String type, final JobIntent intent) {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getIntent() == intent)
        .filter(r -> r.getValue() instanceof final JobRecordValue job && job.getType().equals(type))
        .count();
  }

  private static double activationRequests(final List<MeterRegistry> registries) {
    return registries.stream()
        .mapToDouble(
            r -> counter(r, "zeebe.gateway.total.requests", "requestType", "JOB_BATCH#ACTIVATE"))
        .sum();
  }

  private static double counter(
      final MeterRegistry registry, final String name, final String... tags) {
    return registry.find(name).tags(tags).counters().stream().mapToDouble(Counter::count).sum();
  }

  private static double notificationMessages(final MeterRegistry registry) {
    return registry.find("zeebe.messaging.request.count").counters().stream()
        .filter(c -> String.valueOf(c.getId().getTag("topic")).endsWith("jobsAvailable"))
        .mapToDouble(Counter::count)
        .sum();
  }
}
