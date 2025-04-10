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

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.processing.user.IdentitySetupInitializer;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.snapshots.impl.FileBasedSnapshotId;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@ZeebeIntegration
final class IdentitySetupInitializerIT {

  private static PasswordEncoder passwordEncoder;
  @AutoClose private CamundaClient client;
  @AutoClose private TestStandaloneBroker broker;

  @BeforeAll
  static void beforeAll() {
    passwordEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
  }

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void shouldInitializeIdentity(final boolean enableAuthorizations) {
    // given a broker with authorization enabled or disabled
    final var username = Strings.newRandomValidUsername();
    final var name = UUID.randomUUID().toString();
    final var password = UUID.randomUUID().toString();
    final var email = UUID.randomUUID().toString();
    createBroker(
        enableAuthorizations,
        1,
        cfg -> {
          final var user = new ConfiguredUser(username, password, name, email);
          cfg.getInitialization().setUsers(List.of(user));
        });

    // then identity should be initialized
    final var record =
        RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZE)
            .getFirst()
            .getValue();

    final var createdUser = record.getUsers().getFirst();
    Assertions.assertThat(createdUser)
        .isNotNull()
        .hasUsername(username)
        .hasName(name)
        .hasEmail(email);
    final var passwordMatches = passwordEncoder.matches(password, createdUser.getPassword());
    assertTrue(passwordMatches);

    final var createdRole = record.getDefaultRole();
    Assertions.assertThat(createdRole).hasName(IdentitySetupInitializer.DEFAULT_ROLE_NAME);

    final var createdTenant = record.getDefaultTenant();
    Assertions.assertThat(createdTenant)
        .hasTenantId(IdentitySetupInitializer.DEFAULT_TENANT_ID)
        .hasName(IdentitySetupInitializer.DEFAULT_TENANT_NAME);
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
        RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZE).limit(2).toList();

    assertThat(initializeRecords)
        .describedAs("One partition should initialize identity and distribute it to the other")
        .hasSize(2);

    final var partition1Record =
        initializeRecords.stream().filter(r -> r.getKey() == -1L).findFirst().orElseThrow();
    final var partition2Record =
        initializeRecords.stream().filter(r -> r.getKey() != -1L).findFirst().orElseThrow();

    assertThat(partition1Record.getPartitionId()).isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(partition2Record.getPartitionId()).isNotEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(
            Protocol.decodePartitionId(partition2Record.getValue().getDefaultRole().getRoleKey()))
        .describedAs("Role key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);
    assertThat(
            Protocol.decodePartitionId(
                partition2Record.getValue().getUsers().getFirst().getUserKey()))
        .describedAs("User key should be generated on the deployment partition")
        .isEqualTo(Protocol.DEPLOYMENT_PARTITION);
  }

  @Test
  @Disabled("https://github.com/camunda/camunda/issues/30109")
  void shouldNotRecreateEntitiesOnRestart(@TempDir final Path tempDir) {
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

    // then the next Initialize command is rejected
    assertThat(
            RecordingExporter.records()
                .onlyCommandRejections()
                .withIntent(IdentitySetupIntent.INITIALIZE)
                .exists())
        .isTrue();
  }

  @Test
  void shouldInitializeWithMultipleConfiguredUsers() {
    // given a broker with authorization enabled
    final var user1 =
        new ConfiguredUser(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    final var user2 =
        new ConfiguredUser(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    createBroker(
        true,
        1,
        cfg -> {
          cfg.getInitialization().setUsers(List.of(user1, user2));
        });

    // then identity should be initialized
    final var record =
        RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZE)
            .getFirst()
            .getValue();

    final var firstUser = record.getUsers().getFirst();
    Assertions.assertThat(firstUser)
        .isNotNull()
        .hasUsername(user1.getUsername())
        .hasName(user1.getName())
        .hasEmail(user1.getEmail());
    assertTrue(passwordEncoder.matches(user1.getPassword(), firstUser.getPassword()));

    final var secondUser = record.getUsers().getLast();
    Assertions.assertThat(secondUser)
        .isNotNull()
        .hasUsername(user2.getUsername())
        .hasName(user2.getName())
        .hasEmail(user2.getEmail());
    assertTrue(passwordEncoder.matches(user2.getPassword(), secondUser.getPassword()));
  }

  private void createBroker(
      final boolean authorizationsEnabled,
      final int partitionCount,
      final Path tempDir,
      final Consumer<CamundaSecurityProperties> securityCfg) {
    broker =
        new TestStandaloneBroker()
            .withSecurityConfig(cfg -> cfg.getAuthorizations().setEnabled(authorizationsEnabled))
            .withSecurityConfig(securityCfg)
            .withBrokerConfig(cfg -> cfg.getCluster().setPartitionsCount(partitionCount))
            .withRecordingExporter(true);

    if (tempDir != null) {
      broker.withWorkingDirectory(tempDir);
    }

    broker.start().awaitCompleteTopology(1, partitionCount, 1, Duration.ofSeconds(30));
    client = broker.newClientBuilder().build();
  }

  private void createBroker(final boolean authorizationsEnabled, final int partitionCount) {
    createBroker(
        authorizationsEnabled,
        partitionCount,
        cfg -> {
          final var user = new ConfiguredUser("demo", "demo", "Demo", "demo@demo.com");
          cfg.getInitialization().getUsers().add(user);
        });
  }

  private void createBroker(
      final boolean authorizationsEnabled, final int partitionCount, final Path tempDir) {
    createBroker(authorizationsEnabled, partitionCount, tempDir, cfg -> {});
  }

  private void createBroker(
      final boolean authorizationsEnabled,
      final int partitionCount,
      final Consumer<CamundaSecurityProperties> securityCfg) {
    createBroker(authorizationsEnabled, partitionCount, null, securityCfg);
  }
}
