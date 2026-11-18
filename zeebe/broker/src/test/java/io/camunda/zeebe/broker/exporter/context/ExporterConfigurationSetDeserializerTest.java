/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * Unit tests for {@link ExporterConfigurationSetDeserializer}. Set properties accept the same
 * shapes as List properties, and unexpected tokens fail the same way.
 *
 * <p>All scenarios are exercised through {@link ExporterConfiguration#fromArgs}.
 */
@Execution(ExecutionMode.CONCURRENT)
final class ExporterConfigurationSetDeserializerTest {

  @Test
  void shouldDeserializeMapWithNumericKeysAsSet() {
    // given — Spring Boot flattens categories_0..2 to {"0", "1", "2"}; with no Set deserializer,
    // Jackson rejected the START_OBJECT token
    final var args =
        Map.<String, Object>of(
            "items", Map.of("0", "ADMIN", "1", "USER_TASKS", "2", "DEPLOYED_RESOURCES"));

    // when
    final var instance = ExporterConfiguration.fromArgs(CategorySetConfig.class, args);

    // then
    assertThat(instance.items())
        .containsExactlyInAnyOrder(
            Category.ADMIN, Category.USER_TASKS, Category.DEPLOYED_RESOURCES);
  }

  @Test
  void shouldDeserializeJsonArrayAsSetWithoutDuplicates() {
    // given — a JSON array must keep working after the Set deserializer is registered
    final var args = Map.<String, Object>of("items", List.of("ADMIN", "ADMIN", "USER_TASKS"));

    // when
    final var instance = ExporterConfiguration.fromArgs(CategorySetConfig.class, args);

    // then
    assertThat(instance.items()).containsExactlyInAnyOrder(Category.ADMIN, Category.USER_TASKS);
  }

  @Test
  void shouldDeserializeEmptyStringAsEmptySet() {
    // given — Spring Boot flattens an empty YAML list to an empty string, as for List
    final var args = Map.<String, Object>of("items", "");

    // when
    final var instance = ExporterConfiguration.fromArgs(CategorySetConfig.class, args);

    // then
    assertThat(instance.items()).isEmpty();
  }

  @Test
  void shouldDeserializeNonEmptyStringAsSingletonSet() {
    // given — a single configured value without array syntax
    final var args = Map.<String, Object>of("items", "ADMIN");

    // when
    final var instance = ExporterConfiguration.fromArgs(CategorySetConfig.class, args);

    // then
    assertThat(instance.items()).containsExactly(Category.ADMIN);
  }

  @Test
  void shouldThrowOnUnexpectedTokenTypeForSet() {
    // given — an integer is not a valid token for a Set property
    final var args = Map.<String, Object>of("items", 42);

    // when - then
    assertThatCode(() -> ExporterConfiguration.fromArgs(CategorySetConfig.class, args))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Expected START_ARRAY, START_OBJECT, or VALUE_STRING");
  }

  @Test
  void shouldDeserializeIndexedSetNestedInObjectProperty() {
    // given — Spring Boot flattens client.categories[0..1] under a nested object
    final var args =
        Map.<String, Object>of(
            "client", Map.of("categories", Map.of("0", "ADMIN", "1", "USER_TASKS")));

    // when
    final var instance = ExporterConfiguration.fromArgs(NestedSetConfig.class, args);

    // then
    assertThat(instance.getClient().getCategories())
        .containsExactlyInAnyOrder(Category.ADMIN, Category.USER_TASKS);
  }

  private record CategorySetConfig(Set<Category> items) {}

  // Fluent setters and a Set one level down, as in AuditLogConfiguration.
  private static final class NestedSetConfig {
    private ActorConfig client = new ActorConfig();

    public ActorConfig getClient() {
      return client;
    }

    public NestedSetConfig setClient(final ActorConfig client) {
      this.client = client;
      return this;
    }
  }

  private static final class ActorConfig {
    private Set<Category> categories = Set.of();

    public Set<Category> getCategories() {
      return categories;
    }

    public ActorConfig setCategories(final Set<Category> categories) {
      this.categories = categories;
      return this;
    }
  }

  private enum Category {
    ADMIN,
    USER_TASKS,
    DEPLOYED_RESOURCES
  }
}
