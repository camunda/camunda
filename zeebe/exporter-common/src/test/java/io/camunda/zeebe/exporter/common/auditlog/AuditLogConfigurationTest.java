/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.webapps.schema.entities.auditlog.AuditLogActorType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogEntityType;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationCategory;
import io.camunda.webapps.schema.entities.auditlog.AuditLogOperationType;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration.ActorAuditLogConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogInfo.AuditLogActor;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AuditLogConfigurationTest {

  @Test
  void shouldHaveDefaultUserAndClientConfigurations() {
    final var config = new AuditLogConfiguration();

    assertThat(config.getUser()).isNotNull();
    assertThat(config.getClient()).isNotNull();
    assertThat(config.getUser().getCategories()).isNotEmpty();
    assertThat(config.getClient().getCategories()).isNotEmpty();
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
    config.getUser().setCategories(Set.of(AuditLogOperationCategory.OPERATOR));

    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  void shouldBeEnabledWhenClientCategoryConfigured() {
    final var config = new AuditLogConfiguration();
    config.getClient().setCategories(Set.of(AuditLogOperationCategory.OPERATOR));
    config.getUser().setCategories(Set.of());

    assertThat(config.isEnabled()).isTrue();
  }

  @Test
  void shouldBeEnabledForUserWhenCategoryMatches() {
    final var config = new AuditLogConfiguration();
    config
        .getUser()
        .setCategories(Set.of(AuditLogOperationCategory.OPERATOR))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.OPERATOR,
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
        .setCategories(Set.of(AuditLogOperationCategory.USER_TASK))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.USER_TASK,
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
        .setCategories(Set.of(AuditLogOperationCategory.OPERATOR))
        .setExcludes(Set.of());

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.USER_TASK,
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
            AuditLogOperationCategory.OPERATOR,
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
        .setCategories(Set.of(AuditLogOperationCategory.OPERATOR))
        .setExcludes(Set.of(AuditLogEntityType.VARIABLE));

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.OPERATOR,
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
        .setCategories(Set.of(AuditLogOperationCategory.OPERATOR))
        .setExcludes(Set.of(AuditLogEntityType.VARIABLE));

    final var auditLog =
        new AuditLogInfo(
            AuditLogOperationCategory.OPERATOR,
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
      final var config = new ActorAuditLogConfiguration();

      assertThat(config.getCategories())
          .containsExactlyInAnyOrder(
              AuditLogOperationCategory.OPERATOR,
              AuditLogOperationCategory.USER_TASK,
              AuditLogOperationCategory.ADMIN);
    }

    @Test
    void shouldHaveDefaultExcludes() {
      final var config = new ActorAuditLogConfiguration();

      assertThat(config.getExcludes()).containsExactly(AuditLogEntityType.VARIABLE);
    }
  }
}
