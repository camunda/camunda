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

import io.atomix.cluster.MemberId;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.it.logarchive.LogArchiveBucket.FrameRef;
import io.camunda.zeebe.journal.JournalMetaStore.InMemory;
import io.camunda.zeebe.journal.file.SegmentedJournal;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * When the leader of a replicated partition goes away, the new leader continues the same log
 * archive from F: ranges stay contiguous across terms, and the archive only ever describes
 * committed entries.
 */
@ZeebeIntegration
final class LogArchiveLeaderChangeIT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveLeaderChangeIT.class);
  private static final int PARTITION = 1;
  private static final int BROKERS = 3;
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");
  private final String jobType = "job-" + UUID.randomUUID();

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS)
          .withPartitionsCount(1)
          .withReplicationFactor(BROKERS)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              (id, broker) ->
                  LogArchiveBucket.enableLogArchive(
                      broker.withUnifiedConfig(
                          cfg -> {
                            LogArchiveBucket.configureStore(cfg, basePath);
                            cfg.getData()
                                .getPrimaryStorage()
                                .setDirectory(dataDirectory(id).toString());
                          })))
          .build();

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    cluster.start().awaitCompleteTopology();
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldContinueTheLogArchiveOnTheNewLeader() {
    // given -- the first leader archives
    final long process;
    try (final var client = cluster.newClientBuilder().build()) {
      process = deployProcess(client);
      createInstances(client, process, 100);
    }
    final var oldLeader = cluster.leaderForPartition(PARTITION);
    final long positionBeforeChange = processedPosition(oldLeader);
    await("the first leader archives up to the processed position")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () ->
                assertThat(bucket.tryView(TIMELINE, PARTITION))
                    .hasValueSatisfying(
                        view ->
                            assertThat(view.archivedPosition())
                                .isGreaterThanOrEqualTo(positionBeforeChange)));
    final long oldTerm =
        maxTerm(LogArchiveBucket.objects(bucket.tryView(TIMELINE, PARTITION).orElseThrow()));

    // when -- the leader stops while there is unarchived log, and load continues on a new leader
    try (final var client = cluster.newClientBuilder().build()) {
      createInstances(client, process, 30);
    }
    oldLeader.stop();
    final var newLeader = awaitNewLeader(oldLeader);
    try (final var client = cluster.newClientBuilder().build()) {
      await("the new leader accepts commands")
          .atMost(Duration.ofSeconds(60))
          .ignoreExceptions()
          .until(() -> createInstance(client, process) > 0);
      createInstances(client, process, 100);
    }
    final long positionAfterChange = processedPosition(newLeader);

    // then -- the new leader archives in a higher term, continuing from F
    await("the new leader archives up to the processed position in a higher term")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var view = bucket.tryView(TIMELINE, PARTITION).orElseThrow();
              assertThat(view.archivedPosition()).isGreaterThanOrEqualTo(positionAfterChange);
              assertThat(maxTerm(LogArchiveBucket.objects(view))).isGreaterThan(oldTerm);
            });

    // stop all brokers so that neither the bucket nor the journals change while being read
    final var survivors = cluster.brokers().values().stream().filter(b -> b != oldLeader).toList();
    cluster.shutdown();
    final var archive = bucket.readAndVerify(TIMELINE, PARTITION);
    LOG.info("Verified the log archive: {}", archive.summary());
    assertThat(archive.objects()).extracting(LogObjectJson::term).contains(oldTerm);
    assertThat(archive.objects())
        .filteredOn(o -> o.term() > oldTerm)
        .first()
        .satisfies(
            firstOfNewTerm ->
                assertThat(archive.objects())
                    .filteredOn(o -> o.lastIndex() == firstOfNewTerm.firstIndex() - 1)
                    .singleElement()
                    .extracting(LogObjectJson::term)
                    .isEqualTo(oldTerm));

    // and -- every archived entry is the committed entry at its index on every surviving replica,
    // so no archived object holds an entry that was not committed
    for (final var survivor : survivors) {
      assertArchivedEntriesMatchJournal(archive.frames(), dataDirectory(survivor.nodeId()));
    }
  }

  private static void assertArchivedEntriesMatchJournal(
      final List<FrameRef> frames, final Path dataDirectory) {
    final var partitionDirectory =
        dataDirectory.resolve("default").resolve("partitions").resolve(String.valueOf(PARTITION));
    try (final var journal =
            SegmentedJournal.builder(new SimpleMeterRegistry())
                .withDirectory(partitionDirectory.toFile())
                .withName("default-partition-" + PARTITION)
                .withMetaStore(new InMemory())
                .build();
        final var reader = journal.openReader()) {
      final long firstIndex = journal.getFirstIndex();
      final var compared = new ArrayList<FrameRef>();
      for (final var frame : frames) {
        if (frame.index() < firstIndex) {
          continue;
        }
        assertThat(reader.seek(frame.index()))
            .as("journal of %s has index %d", dataDirectory, frame.index())
            .isEqualTo(frame.index());
        assertThat(reader.hasNext()).isTrue();
        final var record = reader.next();
        assertThat(new FrameRef(record.index(), frame.term(), record.asqn(), record.checksum()))
            .as("journal entry of %s at index %d", dataDirectory, frame.index())
            .isEqualTo(frame);
        compared.add(frame);
      }
      // compaction is capped at F and snapshots are rare here, so most of the archive is compared
      assertThat(compared)
          .as("archived entries compared against the journal of %s", dataDirectory)
          .isNotEmpty()
          .extracting(FrameRef::term)
          .containsAll(frames.stream().map(FrameRef::term).distinct().toList());
    }
  }

  private TestStandaloneBroker awaitNewLeader(final TestStandaloneBroker oldLeader) {
    return await("a new leader is elected")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions()
        .until(() -> cluster.leaderForPartition(PARTITION), leader -> leader != oldLeader);
  }

  private Path dataDirectory(final MemberId brokerId) {
    return testDirectory.resolve("broker-" + brokerId.id()).toAbsolutePath();
  }

  private static long processedPosition(final TestStandaloneBroker broker) {
    return PartitionsActuator.of(broker).query().get(PARTITION).processedPosition();
  }

  private static long maxTerm(final List<LogObjectJson> objects) {
    return objects.stream().mapToLong(LogObjectJson::term).max().orElse(-1);
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

  private static long createInstance(final CamundaClient client, final long process) {
    return client
        .newCreateInstanceCommand()
        .processDefinitionKey(process)
        .variables(Map.of("payload", RandomStringUtils.insecure().nextAlphanumeric(1024)))
        .send()
        .join()
        .getProcessInstanceKey();
  }

  private static void createInstances(
      final CamundaClient client, final long process, final int count) {
    for (int i = 0; i < count; i++) {
      createInstance(client, process);
    }
  }
}
