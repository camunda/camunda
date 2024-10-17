/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static io.camunda.zeebe.protocol.record.RecordValueAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.engine.processing.user.DefaultUserCreator;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.assertj.core.api.Assertions;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ZeebeIntegration
final class DefaultUserCreatorIT {

  private static PasswordEncoder passwordEncoder;
  @AutoCloseResource private ZeebeClient client;
  @AutoCloseResource private TestStandaloneBroker broker;

  @BeforeAll
  static void beforeAll() {
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Test
  void shouldCreateDefaultUser() throws InterruptedException {
    // given a broker with authorization enabled
    createBroker(true, 1);

    // then default user should be created
    final var createdUser =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(DefaultUserCreator.DEFAULT_USER_USERNAME)
            .getFirst()
            .getValue();

    assertThat(createdUser)
        .isNotNull()
        .hasFieldOrPropertyWithValue("username", DefaultUserCreator.DEFAULT_USER_USERNAME)
        .hasFieldOrPropertyWithValue("name", DefaultUserCreator.DEFAULT_USER_USERNAME)
        .hasFieldOrPropertyWithValue("email", DefaultUserCreator.DEFAULT_USER_EMAIL)
        .hasFieldOrPropertyWithValue("userType", UserType.DEFAULT);
    final var passwordMatches =
        passwordEncoder.matches(
            DefaultUserCreator.DEFAULT_USER_PASSWORD, createdUser.getPassword());
    assertTrue(passwordMatches);
  }

  @Test
  void shouldNotCreateDefaultUserWhenAuthorizationsDisabled() {
    // given a broker with authorization disabled
    createBroker(false, 1);

    // then default user should not be created
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newClockResetCommand().send().join();

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ClockIntent.RESET)
                .withIntent(UserIntent.CREATE)
                .toList())
        .describedAs("No user CREATE command must be written")
        .isEmpty();
  }

  @Test
  void shouldOnlyCreateDefaultUserOnDeploymentPartition() {
    // given a broker with authorization disabled
    createBroker(true, 2);

    // then default user should not be created
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newClockResetCommand().send().join();

    final var userCreateRecords =
        RecordingExporter.records()
            .limit(r -> r.getIntent() == ClockIntent.RESET)
            .userRecords()
            .withIntent(UserIntent.CREATED)
            .toList();

    Assertions.assertThat(userCreateRecords)
        .describedAs("One partition should CREATE user and distribute it to the other")
        .hasSize(2);

    final var firstRecord = userCreateRecords.getFirst();
    final var secondRecord = userCreateRecords.getLast();

    Assertions.assertThat(firstRecord.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    Assertions.assertThat(Protocol.decodePartitionId(firstRecord.getValue().getUserKey()))
        .describedAs("User key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);

    Assertions.assertThat(secondRecord.getPartitionId())
        .isNotEqualTo(Protocol.DEPLOYMENT_PARTITION);
    Assertions.assertThat(Protocol.decodePartitionId(secondRecord.getValue().getUserKey()))
        .describedAs(
            "User key should be generated on the deployment partition and distributed to this partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);

    Assertions.assertThat(firstRecord.getValue()).isEqualTo(secondRecord.getValue());
  }

  @Test
  void shouldNotCreateDefaultUserOnRestart(@TempDir final Path tempDir) {
    // given a broker with authorization enabled
    createBroker(true, 1, tempDir);

    RecordingExporter.userRecords(UserIntent.CREATED).limit(1).await();

    // when broker is restarted
    final var partitions = PartitionsActuator.of(broker);
    partitions.takeSnapshot();
    Awaitility.await("Snapshot is taken")
        .atMost(Duration.ofSeconds(60))
        .until(
            () ->
                Optional.ofNullable(partitions.query().get(1).snapshotId())
                    .flatMap(FileBasedSnapshotId::ofFileName),
            Optional::isPresent)
        .orElseThrow();
    broker.stop();
    broker.start().awaitCompleteTopology();

    // then default user should be created once
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newClockResetCommand().send().join();

    Assertions.assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ClockIntent.RESET)
                .withIntent(UserIntent.CREATE)
                .toList())
        .describedAs("Only one user CREATE command must be written")
        .hasSize(1);
  }

  private void createBroker(
      final boolean authorizationsEnabled, final int partitionCount, final Path tempDir) {
    broker =
        new TestStandaloneBroker()
            .withBrokerConfig(
                cfg ->
                    cfg.getExperimental()
                        .getEngine()
                        .getAuthorizations()
                        .setEnableAuthorization(authorizationsEnabled))
            .withBrokerConfig(cfg -> cfg.getCluster().setPartitionsCount(partitionCount))
            .withRecordingExporter(true);

    if (tempDir != null) {
      broker.withWorkingDirectory(tempDir);
    }

    broker.start().awaitCompleteTopology(1, partitionCount, 1, Duration.ofSeconds(30));
    client = broker.newClientBuilder().build();
  }

  private void createBroker(final boolean authorizationsEnabled, final int partitionCount) {
    createBroker(authorizationsEnabled, partitionCount, null);
  }
}
