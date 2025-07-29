/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.application.commons.security.CamundaSecurityConfiguration.CamundaSecurityProperties;
import io.camunda.client.CamundaClient;
import io.camunda.security.configuration.ConfiguredMappingRule;
import io.camunda.security.configuration.ConfiguredUser;
import io.camunda.zeebe.engine.processing.user.IdentitySetupInitializer;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ClockIntent;
import io.camunda.zeebe.protocol.record.intent.IdentitySetupIntent;
import io.camunda.zeebe.protocol.record.intent.RoleIntent;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.RoleRecordValue;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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
    final var createdUser =
        RecordingExporter.userRecords(UserIntent.CREATED)
            .withUsername(username)
            .getFirst()
            .getValue();
    Assertions.assertThat(createdUser)
        .isNotNull()
        .hasUsername(username)
        .hasName(name)
        .hasEmail(email);
    final var passwordMatches = passwordEncoder.matches(password, createdUser.getPassword());
    assertThat(passwordMatches).isTrue();

    assertThat(RecordingExporter.roleRecords(RoleIntent.CREATED).limit(4))
        .extracting(record -> record.getValue().getName())
        .containsExactlyInAnyOrder("Readonly Admin", "Admin", "RPA", "Connectors");

    final var createdTenant =
        RecordingExporter.tenantRecords(TenantIntent.CREATED).getFirst().getValue();
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

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ClockIntent.RESET))
                .identitySetupRecords()
                .withIntent(IdentitySetupIntent.INITIALIZED))
        .hasSize(1)
        .extracting(Record::getPartitionId)
        .containsOnly(Protocol.DEPLOYMENT_PARTITION);
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
    assertThat(passwordEncoder.matches(user1.getPassword(), firstUser.getPassword())).isTrue();

    final var secondUser = record.getUsers().getLast();
    Assertions.assertThat(secondUser)
        .isNotNull()
        .hasUsername(user2.getUsername())
        .hasName(user2.getName())
        .hasEmail(user2.getEmail());
    assertThat(passwordEncoder.matches(user2.getPassword(), secondUser.getPassword())).isTrue();
  }

  @Test
  void shouldInitializeWithMappingRulesAndRoleMemberships() {
    // given a broker with authorization enabled
    final var mappingRules1 =
        new ConfiguredMappingRule(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    final var mappingRules2 =
        new ConfiguredMappingRule(
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    createBroker(
        true,
        1,
        cfg -> {
          cfg.getInitialization().setMappingRules(List.of(mappingRules1, mappingRules2));
          cfg.getInitialization()
              .getDefaultRoles()
              .put(
                  "admin",
                  Map.of(
                      "mappingRules",
                      List.of(mappingRules1.getMappingRuleId()),
                      "mappingrules",
                      List.of(mappingRules2.getMappingRuleId())));
        });

    // then identity should be initialized
    final var record =
        RecordingExporter.identitySetupRecords(IdentitySetupIntent.INITIALIZE)
            .getFirst()
            .getValue();

    final var firstMappingRule = record.getMappingRules().getFirst();
    Assertions.assertThat(firstMappingRule)
        .isNotNull()
        .hasMappingRuleId(mappingRules1.getMappingRuleId())
        .hasClaimName(mappingRules1.getClaimName())
        .hasClaimValue(mappingRules1.getClaimValue());

    final var secondMappingRule = record.getMappingRules().get(1);
    Assertions.assertThat(secondMappingRule)
        .isNotNull()
        .hasMappingRuleId(mappingRules2.getMappingRuleId())
        .hasClaimName(mappingRules2.getClaimName())
        .hasClaimValue(mappingRules2.getClaimValue());

    final var members = record.getRoleMembers().stream().map(RoleRecordValue::getEntityId).toList();
    assertThat(members)
        .containsExactlyInAnyOrder(
            mappingRules1.getMappingRuleId(), mappingRules2.getMappingRuleId());
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
          final var user = new ConfiguredUser("demo", "demo", "Demo", "demo@example.com");
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
