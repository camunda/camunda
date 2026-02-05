/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogActorType;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration.ActorAuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditLogConfigurationTest {

  @Test
  void shouldBeEnabledByDefault() {
    final var config = new AuditLogConfiguration();

    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  void shouldBeDisabled() {
    final var config = new AuditLogConfiguration();
    config.setEnabled(false);

    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void shouldHaveDefaultUserAndClientConfigurations() {
    final var config = new AuditLogConfiguration();

    assertThat(config.getUser()).isNotNull();
    assertThat(config.getClient()).isNotNull();
    assertThat(config.getUser().getCategories())
        .as("User categories should include all categories by default")
        .containsExactlyInAnyOrder(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogOperationCategory.USER_TASKS,
            AuditLogOperationCategory.ADMIN);
    assertThat(config.getClient().getCategories())
        .as("Client categories should be empty by default (opt-in logging)")
        .isEmpty();
  }

  @Test
  void shouldBeDisabledWhenAllCategoriesAreEmpty() {
    final var config = new AuditLogConfiguration();
    config.getUser().setCategories(Set.of());
    config.getClient().setCategories(Set.of());

    assertThat(config.isEnabled()).isFalse();
  }

  @Test
  void shouldBeEnabledWhenUserCategoryConfigured() {
    final var config = new AuditLogConfiguration();
    config.getClient().setCategories(Set.of());
    config.getUser().setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES));

    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  void shouldBeEnabledWhenClientCategoryConfigured() {
    final var config = new AuditLogConfiguration();
    config.getClient().setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES));
    config.getUser().setCategories(Set.of());

    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  void shouldBeDisabledForAnonymousActors() {
    final var config = new AuditLogConfiguration();

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.MODIFY,
            AuditLogActor.anonymous(),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isFalse();
  }

  @Test
  void shouldBeEnabledForUnknownActors() {
    final var config = new AuditLogConfiguration();

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.MODIFY,
            AuditLogActor.unknown(),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isTrue();
  }

  @Test
  void shouldBeEnabledForUserWhenCategoryMatches() {
    final var config = new AuditLogConfiguration();
    config
        .getUser()
        .setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.MODIFY,
            new AuditLogActor(AuditLogActorType.USER, "test-user"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isTrue();
  }

  @Test
  void shouldBeEnabledForClientWhenCategoryMatches() {
    final var config = new AuditLogConfiguration();
    config
        .getClient()
        .setCategories(Set.of(AuditLogOperationCategory.USER_TASKS))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.USER_TASKS,
            AuditLogEntityType.USER_TASK,
            AuditLogOperationType.UNKNOWN,
            new AuditLogActor(AuditLogActorType.CLIENT, "test-client"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isTrue();
  }

  @Test
  void shouldBeDisabledForUserWhenCategoryDoesNotMatch() {
    final var config = new AuditLogConfiguration();
    config
        .getUser()
        .setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.USER_TASKS,
            AuditLogEntityType.USER_TASK,
            AuditLogOperationType.UNKNOWN,
            new AuditLogActor(AuditLogActorType.USER, "test-user"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isFalse();
  }

  @Test
  void shouldBeDisabledForClientWhenCategoryDoesNotMatch() {
    final var config = new AuditLogConfiguration();
    config.getClient().setCategories(Set.of(AuditLogOperationCategory.ADMIN)).setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.MODIFY,
            new AuditLogActor(AuditLogActorType.CLIENT, "test-client"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isFalse();
  }

  @Test
  void shouldBeDisabledWhenEntityTypeIsExcluded() {
    final var config = new AuditLogConfiguration();
    config
        .getUser()
        .setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES))
        .setExcludes(Set.of(AuditLogEntityType.VARIABLE));

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.VARIABLE,
            AuditLogOperationType.UNKNOWN,
            new AuditLogActor(AuditLogActorType.USER, "test-user"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isFalse();
  }

  @Test
  void shouldBeEnabledWhenEntityTypeIsNotExcluded() {
    final var config = new AuditLogConfiguration();
    config
        .getUser()
        .setCategories(Set.of(AuditLogOperationCategory.DEPLOYED_RESOURCES))
        .setExcludes(Set.of(AuditLogEntityType.VARIABLE));

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.DEPLOYED_RESOURCES,
            AuditLogEntityType.PROCESS_INSTANCE,
            AuditLogOperationType.MODIFY,
            new AuditLogActor(AuditLogActorType.USER, "test-user"),
            Optional.empty());

    assertThat(config.isEnabled(auditLog)).isTrue();
  }

  @Nested
  class ActorAuditLogConfigurationTest {
    @Test
    void shouldHaveDefaultCategories() {
      final var config = ActorAuditLogConfiguration.logAll();

      assertThat(config.getCategories())
          .containsExactlyInAnyOrder(
              AuditLogOperationCategory.DEPLOYED_RESOURCES,
              AuditLogOperationCategory.USER_TASKS,
              AuditLogOperationCategory.ADMIN);
    }

    @Test
    void shouldNotHaveDefaultExcludes() {
      final var config = ActorAuditLogConfiguration.logAll();

      assertThat(config.getExcludes()).isEmpty();
    }
  }
}
