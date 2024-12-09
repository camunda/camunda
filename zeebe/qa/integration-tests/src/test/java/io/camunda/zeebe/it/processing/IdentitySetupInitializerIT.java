/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.engine.processing.user.IdentitySetupInitializer;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.value.UserType;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.junit.AutoCloseResources.AutoCloseResource;
import io.camunda.zeebe.test.util.record.RecordLogger;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ZeebeIntegration
final class IdentitySetupInitializerIT {

  private static final String DEFAULT_USER_USERNAME = "demo";
  private static final String DEFAULT_USER_NAME = "Demo";
  private static final String DEFAULT_USER_PASSWORD = "demo";
  private static final String DEFAULT_USER_EMAIL = "demo@demo.com";

  private static PasswordEncoder passwordEncoder;
  @AutoCloseResource private ZeebeClient client;
  @AutoCloseResource private TestStandaloneBroker broker;

  @BeforeAll
  static void beforeAll() {
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @Test
  void shouldInitializeIdentity() {
    // given a broker with authorization enabled
    createBroker(true, 1);

    // then identity should be initialized
    final var record =
        RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZE)
            .getFirst()
            .getValue();

    final var createdUser = record.getDefaultUser();
    Assertions.assertThat(createdUser)
        .isNotNull()
        .hasUsername(DEFAULT_USER_USERNAME)
        .hasName(DEFAULT_USER_NAME)
        .hasEmail(DEFAULT_USER_EMAIL)
        .hasUserType(UserType.DEFAULT);
    final var passwordMatches =
        passwordEncoder.matches(DEFAULT_USER_PASSWORD, createdUser.getPassword());
    assertTrue(passwordMatches);

    final var createdRole = record.getDefaultRole();
    Assertions.assertThat(createdRole).hasName(IdentitySetupInitializer.DEFAULT_ROLE_NAME);

    final var createdTenant = record.getDefaultTenant();
    Assertions.assertThat(createdTenant)
        .hasTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
        .hasName(IdentitySetupInitializer.DEFAULT_TENANT_NAME);
  }

  @Test
  void shouldNotInitializeIdentityWhenAuthorizationsDisabled() {
    // given a broker with authorization disabled
    createBroker(false, 1);

    // then identity should not be initialized
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    client.newClockResetCommand().send().join();

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent() == ClockIntent.RESETTED)
                .withIntent(IdentitySetupIntent.INITIALIZE)
                .toList())
        .describedAs("No initialize command must be written")
        .isEmpty();

    RecordLogger.logRecords();
  }

  @Test
  void shouldOnlyInitializeIdentityOnDeploymentPartition() {
    // given a broker with authorization disabled
    createBroker(true, 2);

    // identity should not be initialized
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    // We don't join the future, because we are unauthorized to send this command. Joining it will
    // result in an exception.
    client.newClockResetCommand().send();

    final var initializeRecords =
        RecordingExporter.records()
            .limit(
                r ->
                    r.getIntent() == ClockIntent.RESET
                        && r.getRecordType() == RecordType.COMMAND_REJECTION)
            .identitySetupRecords()
            .withIntent(IdentitySetupIntent.INITIALIZED)
            .toList();

    assertThat(initializeRecords)
        .describedAs("One partition should initialize identity and distribute it to the other")
        .hasSize(2);

    final var firstRecord = initializeRecords.getFirst();
    final var secondRecord = initializeRecords.getLast();

    assertThat(firstRecord.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(Protocol.decodePartitionId(firstRecord.getValue().getDefaultRole().getRoleKey()))
        .describedAs("Role key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(Protocol.decodePartitionId(firstRecord.getValue().getDefaultUser().getUserKey()))
        .describedAs("User key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);

    assertThat(secondRecord.getPartitionId()).isNotEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(Protocol.decodePartitionId(secondRecord.getValue().getDefaultRole().getRoleKey()))
        .describedAs("Role key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(Protocol.decodePartitionId(secondRecord.getValue().getDefaultUser().getUserKey()))
        .describedAs("User key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);

    Assertions.assertThat(firstRecord.getValue()).isEqualTo(secondRecord.getValue());
  }

  @Test
  void shouldNotInitializeIdentityTwiceOnRestart(@TempDir final Path tempDir) {
    // given a broker with authorization enabled
    createBroker(true, 1, tempDir);

    RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZED).limit(1).await();

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

    // then identity should only be initialized once
    // We send a clock reset command so we have a record we can limit our RecordingExporter on
    // We don't join the future, because we are unauthorized to send this command. Joining it will
    // result in an exception.
    client.newClockResetCommand().send();

    assertThat(
            RecordingExporter.records()
                .limit(
                    r ->
                        r.getIntent() == ClockIntent.RESET
                            && r.getRecordType() == RecordType.COMMAND_REJECTION)
                .withIntent(IdentitySetupIntent.INITIALIZE)
                .toList())
        .describedAs("Only one initialize command must be written")
        .hasSize(1);
  }

  private void createBroker(
      final boolean authorizationsEnabled, final int partitionCount, final Path tempDir) {
    broker =
        new TestStandaloneBroker()
            .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(authorizationsEnabled))
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
