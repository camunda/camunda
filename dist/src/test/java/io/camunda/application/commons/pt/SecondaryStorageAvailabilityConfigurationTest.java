/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.cluster.PhysicalTenantIds;
import io.camunda.cluster.SecondaryStorageAvailability;
import io.camunda.db.rdbms.RdbmsSchemaManagerRegistry;
import io.camunda.search.schema.SchemaManagerContainer;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class SecondaryStorageAvailabilityConfigurationTest {

  private static final String TENANT_A = "a";

  private ApplicationContextRunner baseRunner() {
    return new ApplicationContextRunner()
        .withUserConfiguration(SecondaryStorageAvailabilityConfiguration.class)
        .withBean(PhysicalTenantIds.class, () -> () -> Set.of(TENANT_A));
  }

  @Test
  void shouldWireSchemaManagerContainerBackedAvailabilityForElasticsearch() {
    // given
    final var schemaManagerContainer = mock(SchemaManagerContainer.class);
    when(schemaManagerContainer.isInitialized(TENANT_A)).thenReturn(true);

    // when/then
    baseRunner()
        .withBean(SchemaManagerContainer.class, () -> schemaManagerContainer)
        .withPropertyValues("camunda.data.secondary-storage.type=elasticsearch")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecondaryStorageAvailability.class);
              assertThat(context).hasSingleBean(SecondaryStorageAvailabilityMetrics.class);
              final var availability = context.getBean(SecondaryStorageAvailability.class);
              assertThat(availability.isAvailable(TENANT_A)).isTrue();
            });
  }

  @Test
  void shouldWireSchemaManagerContainerBackedAvailabilityForOpensearch() {
    // given
    final var schemaManagerContainer = mock(SchemaManagerContainer.class);
    when(schemaManagerContainer.isInitialized(TENANT_A)).thenReturn(true);

    // when/then
    baseRunner()
        .withBean(SchemaManagerContainer.class, () -> schemaManagerContainer)
        .withPropertyValues("camunda.data.secondary-storage.type=opensearch")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecondaryStorageAvailability.class);
              assertThat(context).hasSingleBean(SecondaryStorageAvailabilityMetrics.class);
              final var availability = context.getBean(SecondaryStorageAvailability.class);
              assertThat(availability.isAvailable(TENANT_A)).isTrue();
            });
  }

  @Test
  void shouldWireRdbmsSchemaManagerRegistryBackedAvailabilityForRdbms() {
    // given
    final var rdbmsSchemaManagerRegistry = mock(RdbmsSchemaManagerRegistry.class);
    when(rdbmsSchemaManagerRegistry.isInitialized(TENANT_A)).thenReturn(true);

    // when/then
    baseRunner()
        .withBean(RdbmsSchemaManagerRegistry.class, () -> rdbmsSchemaManagerRegistry)
        .withPropertyValues("camunda.data.secondary-storage.type=rdbms")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecondaryStorageAvailability.class);
              assertThat(context).hasSingleBean(SecondaryStorageAvailabilityMetrics.class);
              final var availability = context.getBean(SecondaryStorageAvailability.class);
              assertThat(availability.isAvailable(TENANT_A)).isTrue();
            });
  }

  @Test
  void shouldWireAlwaysAvailableAvailabilityWhenSecondaryStorageIsNone() {
    baseRunner()
        .withPropertyValues("camunda.data.secondary-storage.type=none")
        .run(
            context -> {
              assertThat(context).hasSingleBean(SecondaryStorageAvailability.class);
              assertThat(context).hasSingleBean(SecondaryStorageAvailabilityMetrics.class);
              assertThat(context.getBean(SecondaryStorageAvailability.class))
                  .isSameAs(SecondaryStorageAvailability.ALWAYS_AVAILABLE);
            });
  }
}
