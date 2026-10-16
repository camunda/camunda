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

import io.camunda.configuration.data.primarystorage.PrimaryStorageBackup.BackupStoreType;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry;
import io.camunda.zeebe.backup.logarchive.model.CommitEntry.Type;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveJson;
import io.camunda.zeebe.backup.logarchive.model.LogArchiveKeys;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import io.camunda.zeebe.backup.logarchive.store.LogArchiveStore;
import io.camunda.zeebe.backup.s3.S3BackupConfig;
import io.camunda.zeebe.backup.s3.S3BackupStore;
import io.camunda.zeebe.backup.s3.S3LogArchiveStore;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.restore.logarchive.LogArchiveRestoreException;
import io.camunda.zeebe.restore.logarchive.LogArchiveView;
import io.camunda.zeebe.test.testcontainers.S3MockTestContainer;
import io.camunda.zeebe.test.util.testcontainers.ContainerLogsDumper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.LongStream;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * A single broker with the log archive enabled archives its committed log into an S3 bucket (MinIO)
 * the same way it does into a filesystem bucket: the commit entries are gapless, snapshots are
 * archived and every archived data object matches the checksum its commit entry lists.
 */
@Testcontainers
@ZeebeIntegration
final class LogArchiveS3IT {

  private static final Logger LOG = LoggerFactory.getLogger(LogArchiveS3IT.class);
  private static final int PARTITION = 1;

  private final String bucketName = RandomStringUtils.insecure().nextAlphabetic(10).toLowerCase();
  private final String jobType = "job-" + UUID.randomUUID();

  @Container private final S3MockTestContainer minio = new S3MockTestContainer();

  @RegisterExtension
  @SuppressWarnings("unused")
  final ContainerLogsDumper logsWatcher =
      new ContainerLogsDumper(() -> Map.of("minio", minio.container()));

  // cannot auto start, as MinIO must be started before the broker can be configured
  @TestZeebe(autoStart = false)
  private final TestStandaloneBroker broker = new TestStandaloneBroker();

  private LogArchiveStore store;

  @BeforeEach
  void setUp() {
    final var config =
        new S3BackupConfig.Builder()
            .withBucketName(bucketName)
            .withEndpoint(minio.externalEndpoint())
            .withRegion(minio.region())
            .withCredentials(minio.accessKey(), minio.secretKey())
            .withApiCallTimeout(Duration.ofSeconds(25))
            .forcePathStyleAccess(true)
            .build();
    try (final var client = S3BackupStore.buildClient(config)) {
      // MinIO may answer 503 right after it started, so retry
      await("until the bucket is created")
          .untilAsserted(
              () -> client.createBucket(builder -> builder.bucket(bucketName).build()).join());
    }
    store = S3LogArchiveStore.of(config);

    broker.withUnifiedConfig(
        cfg -> {
          final var backup = cfg.getData().getPrimaryStorage().getBackup();
          final var s3 = backup.getS3();
          backup.setStore(BackupStoreType.S3);
          s3.setBucketName(bucketName);
          s3.setEndpoint(minio.externalEndpoint());
          s3.setRegion(minio.region());
          s3.setAccessKey(minio.accessKey());
          s3.setSecretKey(minio.secretKey());
          s3.setForcePathStyleAccess(true);
        });
    LogArchiveBucket.enableLogArchive(broker);
    broker.start().awaitCompleteTopology();
  }

  @AfterEach
  void tearDown() {
    store.closeAsync().join();
  }

  @Test
  void shouldArchiveCommittedLogToS3() {
    // given
    final var partitions = PartitionsActuator.of(broker);
    try (final var client = broker.newClientBuilder().build()) {
      final long process =
          client
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

      // when
      for (int i = 0; i < 200; i++) {
        client
            .newCreateInstanceCommand()
            .processDefinitionKey(process)
            .variables(Map.of("payload", RandomStringUtils.insecure().nextAlphanumeric(1024)))
            .send()
            .join();
      }
    }
    final long processedPosition = partitions.query().get(PARTITION).processedPosition();
    partitions.takeSnapshot();

    // then -- F follows the log and a snapshot is archived
    await("F reaches the processed position and a snapshot is archived")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              final var view = tryView();
              assertThat(view).isPresent();
              assertThat(view.get().archivedPosition()).isGreaterThanOrEqualTo(processedPosition);
              assertThat(view.get().snapshots()).isNotEmpty();
            });

    broker.stop();
    final var prefix = LogArchiveKeys.partitionPrefix(TIMELINE, PARTITION);

    final var commitSeqs =
        store.list(prefix + "commits/").join().stream()
            .mapToLong(LogArchiveKeys::seqFromCommitKey)
            .sorted()
            .toArray();
    assertThat(commitSeqs)
        .as("commit entries are gapless from 1")
        .isNotEmpty()
        .containsExactly(LongStream.rangeClosed(1, commitSeqs.length).toArray());

    final var objects = new ArrayList<LogObjectJson>();
    int snapshots = 0;
    for (final long seq : commitSeqs) {
      final var bytes = store.getIfExists(prefix + LogArchiveKeys.commitKey(seq)).join();
      assertThat(bytes).as("commit entry %d", seq).isPresent();
      final var entry = LogArchiveJson.read(bytes.orElseThrow(), CommitEntry.class);
      assertThat(entry.seq()).isEqualTo(seq);
      if (seq == 1) {
        assertThat(entry.type()).isEqualTo(Type.TIMELINE_STARTED);
      }
      if (entry.type() == Type.LOG_ARCHIVED) {
        objects.addAll(entry.objects());
      } else if (entry.type() == Type.SNAPSHOT_ARCHIVED) {
        snapshots++;
      }
    }
    assertThat(snapshots).as("archived snapshots").isPositive();
    assertThat(objects).as("archived data objects").isNotEmpty();
    for (final var object : objects) {
      final var bytes = store.getIfExists(prefix + object.key()).join();
      assertThat(bytes).as("archived object %s exists", object.key()).isPresent();
      assertThat(bytes.orElseThrow()).hasSize((int) object.sizeBytes());
      assertThat(sha256Hex(bytes.orElseThrow()))
          .as("sha256 of %s", object.key())
          .isEqualTo(object.sha256());
    }

    final var view = LogArchiveView.read(store, TIMELINE, PARTITION);
    assertThat(view.lastSeq()).isEqualTo(commitSeqs.length);
    assertThat(view.archivedPosition()).isGreaterThanOrEqualTo(processedPosition);
    assertThat(view.snapshots()).hasSize(snapshots);
    LOG.info(
        "Verified the S3 log archive: commits={}, objects={}, snapshots={}, F=({}, {})",
        commitSeqs.length,
        objects.size(),
        snapshots,
        view.archivedIndex(),
        view.archivedPosition());
  }

  private Optional<LogArchiveView> tryView() {
    try {
      return Optional.of(LogArchiveView.read(store, TIMELINE, PARTITION));
    } catch (final LogArchiveRestoreException e) {
      return Optional.empty();
    }
  }

  private static String sha256Hex(final byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
