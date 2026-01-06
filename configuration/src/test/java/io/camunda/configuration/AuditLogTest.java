/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.beanoverrides.BrokerBasedPropertiesOverride;
import io.camunda.configuration.beans.BrokerBasedProperties;
import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.search.entities.AuditLogEntity.AuditLogOperationCategory;
import io.camunda.zeebe.broker.exporter.context.ExporterConfiguration;
import io.camunda.zeebe.exporter.common.auditlog.AuditLogConfiguration;
import java.util.Set;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

class AuditLogTest {

  @Test
  void shouldBeEnabledByDefault() {
    final AuditLog auditLog = new AuditLog();

    assertThat(auditLog.isEnabled()).as("AuditLog should be enabled by default").isTrue();
  }

  @Test
  void shouldHaveClientDefaults() {
    final AuditLog auditLog = new AuditLog();

    assertThat(auditLog.getClient().getCategories())
        .as("All categories should be enabled for client by default")
        .isEqualTo(
            Set.of(
                AuditLogOperationCategory.DEPLOYED_RESOURCES,
                AuditLogOperationCategory.USER_TASKS,
                AuditLogOperationCategory.ADMIN));
    assertThat(auditLog.getClient().getExcludes())
        .as("No excludes should be set for client by default")
        .isEmpty();
  }

  @Test
  void shouldHaveUserDefaults() {
    final AuditLog auditLog = new AuditLog();

    assertThat(auditLog.getUser().getCategories())
        .as("All categories should be enabled for user by default")
        .isEqualTo(
            Set.of(
                AuditLogOperationCategory.DEPLOYED_RESOURCES,
                AuditLogOperationCategory.USER_TASKS,
                AuditLogOperationCategory.ADMIN));
    assertThat(auditLog.getUser().getExcludes())
        .as("No excludes should be set for user by default")
        .isEmpty();
  }

  @Test
  void shouldConvertToAuditLogConfiguration() {
    final AuditLog auditLog = new AuditLog();

    final AuditLogConfiguration config = auditLog.toConfiguration();

    assertThat(config.isEnabled())
        .as("AuditLogConfiguration should be enabled by default")
        .isEqualTo(auditLog.isEnabled());
    assertThat(config.getClient().getCategories())
        .as("Client categories should match")
        .isEqualTo(auditLog.getClient().getCategories());
    assertThat(config.getUser().getCategories())
        .as("User categories should match")
        .isEqualTo(auditLog.getUser().getCategories());
    assertThat(config.getClient().getExcludes())
        .as("Client excludes should match")
        .isEqualTo(auditLog.getClient().getExcludes());
    assertThat(config.getUser().getExcludes())
        .as("User excludes should match")
        .isEqualTo(auditLog.getUser().getExcludes());
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=rdbms",
        "camunda.data.audit-log.enabled=false",
        "camunda.data.audit-log.user.categories[0]=DEPLOYED_RESOURCES",
        "camunda.data.audit-log.user.categories[1]=USER_TASKS",
        "camunda.data.audit-log.user.excludes[0]=VARIABLE",
        "camunda.data.audit-log.client.categories[0]=ADMIN",
        "camunda.data.audit-log.client.excludes[0]=PROCESS_INSTANCE"
      })
  class RDBMSExporterTest {
    @Test
    void shouldPopulateWithAuditLog(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getRdbmsExporter();

      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.rdbms.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getAuditLog().isEnabled()).isFalse();
      assertThat(config.getAuditLog().getUser().getCategories())
          .containsExactlyInAnyOrder(
              AuditLogOperationCategory.DEPLOYED_RESOURCES, AuditLogOperationCategory.USER_TASKS);
      assertThat(config.getAuditLog().getUser().getExcludes())
          .containsExactlyInAnyOrder(AuditLogEntityType.VARIABLE);
      assertThat(config.getAuditLog().getClient().getCategories())
          .containsExactlyInAnyOrder(AuditLogOperationCategory.ADMIN);
      assertThat(config.getAuditLog().getClient().getExcludes())
          .containsExactlyInAnyOrder(AuditLogEntityType.PROCESS_INSTANCE);
    }
  }

  @Nested
  @ActiveProfiles({"broker"})
  @SpringJUnitConfig({
    UnifiedConfiguration.class,
    UnifiedConfigurationHelper.class,
    BrokerBasedPropertiesOverride.class,
  })
  @TestPropertySource(
      properties = {
        "camunda.data.secondary-storage.type=elasticsearch",
        "camunda.data.audit-log.enabled=true",
        "camunda.data.audit-log.user.categories[0]=DEPLOYED_RESOURCES",
        "camunda.data.audit-log.user.categories[1]=ADMIN",
        "camunda.data.audit-log.user.excludes[0]=INCIDENT",
        "camunda.data.audit-log.client.categories[0]=USER_TASKS",
        "camunda.data.audit-log.client.excludes[0]=USER"
      })
  class CamundaExporterTest {
    @Test
    void shouldPopulateWithAuditLog(@Autowired final BrokerBasedProperties brokerBasedProperties) {
      final var exporter = brokerBasedProperties.getCamundaExporter();

      final var config =
          ExporterConfiguration.fromArgs(
              io.camunda.exporter.config.ExporterConfiguration.class, exporter.getArgs());

      assertThat(config.getAuditLog().isEnabled()).isTrue();
      assertThat(config.getAuditLog().getUser().getCategories())
          .containsExactlyInAnyOrder(
              AuditLogOperationCategory.DEPLOYED_RESOURCES, AuditLogOperationCategory.ADMIN);
      assertThat(config.getAuditLog().getUser().getExcludes())
          .containsExactlyInAnyOrder(AuditLogEntityType.INCIDENT);
      assertThat(config.getAuditLog().getClient().getCategories())
          .containsExactlyInAnyOrder(AuditLogOperationCategory.USER_TASKS);
      assertThat(config.getAuditLog().getClient().getExcludes())
          .containsExactlyInAnyOrder(AuditLogEntityType.USER);
    }
  }
}
