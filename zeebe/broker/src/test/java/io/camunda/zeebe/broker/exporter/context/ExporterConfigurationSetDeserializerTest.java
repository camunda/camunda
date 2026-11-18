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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
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
  void shouldDeserializeElevenIndexedEntriesAsSet() {
    // given — Set reads through the same converter as List, so 11 entries hit the same bug
    final var indexed = new LinkedHashMap<String, Object>();
    IntStream.rangeClosed(0, 10).forEach(i -> indexed.put(String.valueOf(i), "v" + i));
    final var args = Map.<String, Object>of("items", indexed);

    // when
    final var instance = ExporterConfiguration.fromArgs(StringSetConfig.class, args);

    // then
    assertThat(instance.items())
        .containsExactlyInAnyOrderElementsOf(
            IntStream.rangeClosed(0, 10).mapToObj(i -> "v" + i).toList());
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
    // given — Spring Boot flattens client.categories and client.excludes (11 entries) under a
    // nested object
    final var excludes =
        IntStream.range(0, EntityType.values().length)
            .boxed()
            .collect(Collectors.toMap(String::valueOf, i -> EntityType.values()[i].name()));
    final var args =
        Map.<String, Object>of(
            "client",
            Map.of("categories", Map.of("0", "ADMIN", "1", "USER_TASKS"), "excludes", excludes));

    // when
    final var instance = ExporterConfiguration.fromArgs(NestedSetConfig.class, args);

    // then
    assertThat(instance.getClient().getCategories())
        .containsExactlyInAnyOrder(Category.ADMIN, Category.USER_TASKS);
    assertThat(instance.getClient().getExcludes()).containsExactlyInAnyOrder(EntityType.values());
  }

  private record StringSetConfig(Set<String> items) {}

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
    private Set<EntityType> excludes = Set.of();

    public Set<Category> getCategories() {
      return categories;
    }

    public ActorConfig setCategories(final Set<Category> categories) {
      this.categories = categories;
      return this;
    }

    public Set<EntityType> getExcludes() {
      return excludes;
    }

    public ActorConfig setExcludes(final Set<EntityType> excludes) {
      this.excludes = excludes;
      return this;
    }
  }

  private enum Category {
    ADMIN,
    USER_TASKS,
    DEPLOYED_RESOURCES
  }

  private enum EntityType {
    PROCESS_INSTANCE,
    VARIABLE,
    INCIDENT,
    JOB,
    USER_TASK,
    DECISION,
    BATCH,
    USER,
    MAPPING_RULE,
    ROLE,
    GROUP
  }
}
