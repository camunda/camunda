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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.qa.util.cluster.TestCluster;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * During a rolling upgrade, the log archive must not write a single {@code LOG_ARCHIVE} record
 * while any broker still runs an older version, since that broker could not apply it (spec section
 * 18.1). One broker advertises an old version through the test-only property; once it restarts on
 * the current version, the log archive is activated and starts its timeline.
 */
@ZeebeIntegration
final class LogArchiveUpgradeGateIT {

  private static final int PARTITION = 1;
  private static final int BROKERS = 3;
  // not the lowest member id, which runs the checkpoint scheduler
  private static final int OLD_BROKER = 2;
  private static final String ADVERTISED_VERSION_PROPERTY =
      "zeebe.broker.data.backup.logArchive.testOnlyAdvertisedVersion";
  private static @TempDir Path tempDir;

  private final Path testDirectory = tempDir.resolve(UUID.randomUUID().toString());
  private final Path basePath = testDirectory.resolve("bucket");

  @TestZeebe(autoStart = false)
  private final TestCluster cluster =
      TestCluster.builder()
          .withBrokersCount(BROKERS)
          .withPartitionsCount(1)
          .withReplicationFactor(BROKERS)
          .withEmbeddedGateway(true)
          .withBrokerConfig(
              (id, broker) -> {
                LogArchiveBucket.enableLogArchive(
                    broker
                        .withRecordingExporter(true)
                        .withUnifiedConfig(
                            cfg -> {
                              LogArchiveBucket.configureStore(cfg, basePath);
                              cfg.getData()
                                  .getPrimaryStorage()
                                  .setDirectory(dataDirectory(id).toString());
                            }));
                if (id.id().equals(String.valueOf(OLD_BROKER))) {
                  broker.withProperty(ADVERTISED_VERSION_PROPERTY, "8.0.0");
                }
              })
          .build();

  private LogArchiveBucket bucket;

  @BeforeEach
  void setUp() {
    RecordingExporter.reset();
    cluster.start().awaitCompleteTopology();
    bucket = new LogArchiveBucket(basePath);
  }

  @AfterEach
  void tearDown() {
    bucket.close();
  }

  @Test
  void shouldActivateTheLogArchiveOnlyOnceEveryBrokerIsUpgraded() {
    // given -- the cluster processes commands while one broker advertises an old version
    try (final var client = cluster.newClientBuilder().build()) {
      deployProcess(client);
    }

    // then -- no log archive record is written and nothing reaches the bucket
    await("the log archive stays inactive while a broker is not upgraded")
        .during(Duration.ofSeconds(15))
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              assertThat(logArchiveRecordCount()).isZero();
              assertThat(bucket.store().list("log-archive/").join()).isEmpty();
            });

    // when -- the old broker restarts on the current version
    final TestStandaloneBroker oldBroker = cluster.brokers().get(MemberId.from("" + OLD_BROKER));
    oldBroker.stop();
    oldBroker.withProperty(ADVERTISED_VERSION_PROPERTY, "");
    oldBroker.start();
    cluster.awaitCompleteTopology();

    // then -- the log archive is activated and starts its timeline
    await("the log archive starts its timeline once every broker is upgraded")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(logArchiveRecordCount()).isPositive();
              assertThat(bucket.tryView(TIMELINE, PARTITION)).isPresent();
            });
  }

  private static long logArchiveRecordCount() {
    return RecordingExporter.getRecords().stream()
        .filter(r -> r.getValueType() == ValueType.LOG_ARCHIVE)
        .count();
  }

  private Path dataDirectory(final MemberId brokerId) {
    return testDirectory.resolve("broker-" + brokerId.id()).toAbsolutePath();
  }

  private static void deployProcess(final CamundaClient client) {
    client
        .newDeployResourceCommand()
        .addProcessModel(
            Bpmn.createExecutableProcess("process").startEvent().endEvent().done(), "process.bpmn")
        .send()
        .join();
  }
}
