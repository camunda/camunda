/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.logarchive;

import static io.camunda.zeebe.it.logarchive.LogArchiveBucket.TIMELINE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.it.logarchive.StubEventPlane.TimelineKey;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The leader of every partition announces the commit entries of its log archive to the event plane
 * (spec section 19). The event plane, here a stub that implements the seq check, ends up with
 * exactly the bucket's sequence, gap-free, through a leader change, an event plane outage and an
 * event plane that lost its state.
 */
@ZeebeIntegration
final class LogArchiveEventPlaneIT {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final int BROKERS = 3;
  private static final int PARTITIONS = 2;
  private static final String TENANT = "default";
  private static final Duration TIMEOUT = Duration.ofSeconds(60);
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final String clusterId = "cluster-" + UUID.randomUUID();
  private final String jobType = "job-" + UUID.randomUUID();
  private final StubEventPlane eventPlane = new StubEventPlane();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS)
          .withPartitionsCount(PARTITIONS)
          .withReplicationFactor(BROKERS)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              (id, broker) -> {
                final var prefix = "zeebe.broker.data.backup.logArchive.eventPlane.";
                LogArchiveBucket.enableLogArchive(
                        broker.withUnifiedConfig(
                            cfg -> {
                              LogArchiveBucket.configureStore(cfg, basePath);
                              cfg.getData()
                                  .getPrimaryStorage()
                                  .setDirectory(dataDirectory(id).toString());
                            }))
                    .withProperty(prefix + "url", eventPlane.url())
                    .withProperty(prefix + "clusterId", clusterId)
                    .withProperty(prefix + "requestTimeout", "PT2S");
              })
          .build();

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    cluster.start().awaitCompleteTopology();
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    try {
      cluster.shutdown();
    } finally {
      bucket.close();
      eventPlane.close();
    }
  }

  @Test
  void shouldReceiveEveryCommitEntryInOrder() {
    // given
    final long process;
    try (final var client = cluster.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 150);
    }

    // when -- the archive grows while the announcer runs
    // then -- the event plane has the bucket's sequence of every partition, from seq 1
    awaitCaughtUp();
    assertThat(eventPlane.timelines()).hasSize(PARTITIONS);
    assertThat(eventPlane.requests()).noneMatch(r -> r.status() == 400);
    forEachPartition(
        partition ->
            assertThat(eventPlane.entries(key(partition)).size())
                .as("entries of partition %d", partition)
                .isGreaterThan(2));
  }

  @Test
  void shouldStayGapFreeThroughALeaderChange() {
    // given
    final long process;
    try (final var client = cluster.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 60);
    }
    awaitCaughtUp();
    final var oldLeader = cluster.leaderForPartition(1);

    // when -- the leader goes away and load continues on the new leader
    try (final var load = new Load(process)) {
      oldLeader.stop();
      await("a new leader is elected")
          .atMost(TIMEOUT)
          .ignoreExceptions()
          .until(() -> cluster.leaderForPartition(1), leader -> leader != oldLeader);
      final long seqBefore = lastBucketSeq(1);
      await("the new leader archives")
          .atMost(TIMEOUT)
          .until(() -> lastBucketSeq(1) > seqBefore + 2);
    }

    // then -- the event plane catches up with the bucket without gaps or duplicates
    awaitCaughtUp();
    assertThat(eventPlane.requests()).noneMatch(r -> r.status() == 400);
  }

  @Test
  void shouldCatchUpAfterTheEventPlaneWasDown() {
    // given
    final long process;
    try (final var client = cluster.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 40);
    }
    awaitCaughtUp();
    final long archivedBefore = archivedPosition(1);
    final var appliedBefore = appliedSizes();

    // when -- the event plane is down while load continues
    eventPlane.setDown(true);
    try (final var client = cluster.newClientBuilder().build()) {
      createInstances(client, process, 80);
    }

    // then -- archiving goes on, the stub applies nothing
    await("F advances while the event plane is down")
        .atMost(TIMEOUT)
        .untilAsserted(() -> assertThat(archivedPosition(1)).isGreaterThan(archivedBefore));
    assertThat(appliedSizes()).isEqualTo(appliedBefore);

    // and -- once it is up again, the announcer resumes from its nextSeq and catches up
    eventPlane.setDown(false);
    awaitCaughtUp();
    assertThat(appliedSizes().get(1)).isGreaterThan(appliedBefore.get(1));
  }

  @Test
  void shouldResendFromTheStartWhenTheEventPlaneLostItsState() {
    // given
    final long process;
    try (final var client = cluster.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 60);
    }
    awaitCaughtUp();
    final var key = key(1);
    assertThat(eventPlane.entries(key)).hasSizeGreaterThan(2);

    // when -- the event plane forgets the timeline, and the cluster publishes more
    eventPlane.reset(key);
    final int requestsBefore = eventPlane.requests().size();
    try (final var load = new Load(process)) {
      await("the stub is sent entries from seq 1 again")
          .atMost(TIMEOUT)
          .untilAsserted(
              () ->
                  assertThat(
                          eventPlane
                              .requests()
                              .subList(requestsBefore, eventPlane.requests().size()))
                      .anyMatch(r -> r.key().equals(key) && r.status() == 409)
                      .anyMatch(
                          r -> r.key().equals(key) && r.status() == 200 && r.firstSeq() == 1));
    }

    // then -- the whole sequence is rebuilt from the bucket
    awaitCaughtUp();
  }

  /**
   * The stub holds, for every partition, the bucket's commit entries from seq 1, gap-free and equal
   * to {@code commits/<seq>.json}, up to at least the latest seq the bucket had when waiting began.
   */
  private void awaitCaughtUp() {
    final var target = new java.util.HashMap<Integer, Long>();
    forEachPartition(partition -> target.put(partition, lastBucketSeq(partition)));
    await("the event plane has every commit entry up to " + target)
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                target.forEach(
                    (partition, seq) ->
                        assertThat(eventPlane.nextSeq(key(partition)) - 1)
                            .as("last seq of partition %d at the event plane", partition)
                            .isGreaterThanOrEqualTo(seq)));
    forEachPartition(
        partition -> {
          final var entries = eventPlane.entries(key(partition));
          assertThat(entries)
              .extracting(e -> e.get("seq").asLong())
              .containsExactlyElementsOf(
                  LongStream.rangeClosed(1, entries.size()).boxed().toList());
          assertThat(entries.getFirst().get("type").asText()).isEqualTo("TIMELINE_STARTED");
          for (int i = 0; i < entries.size(); i++) {
            assertThat(entries.get(i))
                .as("entry %d of partition %d", i + 1, partition)
                .isEqualTo(bucketEntry(partition, i + 1L));
          }
        });
  }

  private JsonNode bucketEntry(final int partition, final long seq) {
    final var bytes =
        bucket
            .store()
            .getIfExists(
                LogArchiveKeys.partitionPrefix(TIMELINE, partition) + LogArchiveKeys.commitKey(seq))
            .join()
            .orElseThrow();
    try {
      return MAPPER.readTree(bytes);
    } catch (final IOException e) {
      throw new IllegalStateException(e);
    }
  }

  private long lastBucketSeq(final int partition) {
    return bucket
        .store()
        .list(LogArchiveKeys.partitionPrefix(TIMELINE, partition) + "commits/")
        .join()
        .stream()
        .mapToLong(LogArchiveKeys::seqFromCommitKey)
        .max()
        .orElse(0);
  }

  private long archivedPosition(final int partition) {
    return bucket.tryView(TIMELINE, partition).orElseThrow().archivedPosition();
  }

  private Map<Integer, Integer> appliedSizes() {
    return IntStream.rangeClosed(1, PARTITIONS)
        .boxed()
        .collect(java.util.stream.Collectors.toMap(p -> p, p -> eventPlane.entries(key(p)).size()));
  }

  private TimelineKey key(final int partition) {
    return new TimelineKey(clusterId, TENANT, partition, TIMELINE);
  }

  private static void forEachPartition(final java.util.function.IntConsumer action) {
    IntStream.rangeClosed(1, PARTITIONS).forEach(action);
  }

  private Path dataDirectory(final MemberId brokerId) {
    return testDirectory.resolve("broker-" + brokerId.id()).toAbsolutePath();
  }

  private long deployProcess(final CamundaClient client) {
    return client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType(jobType))
                .endEvent()
                .done(),
            "process.bpmn")
        .send()
        .join()
        .getProcesses()
        .getFirst()
        .getProcessDefinitionKey();
  }

  private static void createInstances(
      final CamundaClient client, final long process, final int count) {
    for (int i = 0; i < count; i++) {
      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
    }
  }

  /** Creates process instances in the background until closed, ignoring failures. */
  private final class Load implements AutoCloseable {
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Thread thread;

    private Load(final long process) {
      thread =
          new Thread(
              () -> {
                while (running.get()) {
                  try (final var client = cluster.newClientBuilder().build()) {
                    for (int i = 0; i < 10 && running.get(); i++) {
                      client.newCreateInstanceCommand().processDefinitionKey(process).send().join();
                    }
                  } catch (final Exception e) {
                    // the leader may be gone, try again
                  }
                }
              },
              "event-plane-it-load");
      thread.setDaemon(true);
      thread.start();
    }

    @Override
    public void close() {
      running.set(false);
      try {
        thread.join(30_000);
      } catch (final InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    }
  }
}
