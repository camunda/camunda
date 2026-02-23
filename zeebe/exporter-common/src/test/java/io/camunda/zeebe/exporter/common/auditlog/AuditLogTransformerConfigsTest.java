/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.exporter.common.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.search.entities.AuditLogEntity.AuditLogEntityType;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformer.TransformerConfig;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerConfigs;
import io.camunda.zeebe.exporter.common.auditlog.transformers.AuditLogTransformerRegistry;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Tests to ensure that all audit log transformer configurations are properly set up with correct
 * intent-to-operation mappings.
 */
class AuditLogTransformerConfigsTest {

  /**
   * Intents that have special handling in AuditLogInfo.getOperationType() and don't need to be in
   * the OPERATION_TYPE_MAP. These intents have conditional logic that determines the operation type
   * based on record values.
   */
  private static final Set<Intent> INTENTS_WITH_SPECIAL_HANDLING = Set.of(UserTaskIntent.ASSIGNED);

  /**
   * Verifies that all intents configured in transformer configs are mapped to audit log operation
   * types in AuditLogInfo.OPERATION_TYPE_MAP.
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("allTransformerConfigs")
  void shouldHaveAllIntentsMappedToOperationType(final TransformerConfig config) {
    final Set<Intent> allConfiguredIntents = new HashSet<>();
    allConfiguredIntents.addAll(config.supportedIntents());
    allConfiguredIntents.addAll(config.supportedRejections());

    // Remove intents with special handling
    allConfiguredIntents.removeAll(INTENTS_WITH_SPECIAL_HANDLING);

    final Map<Intent, ?> operationTypeMap = AuditLogInfo.OPERATION_TYPE_MAP;

    for (final Intent intent : allConfiguredIntents) {
      assertThat(operationTypeMap)
          .as(
              "Intent %s (from ValueType %s) should be mapped to an AuditLogOperationType in AuditLogInfo.OPERATION_TYPE_MAP",
              intent.name(), config.valueType())
          .containsKey(intent);
    }
  }

  /** Verifies that all transformer configs have at least one intent or rejection configured. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("allTransformerConfigs")
  void shouldHaveAtLeastOneIntentOrRejectionConfigured(final TransformerConfig config) {
    final boolean hasIntents = !config.supportedIntents().isEmpty();
    final boolean hasRejections = !config.supportedRejections().isEmpty();

    assertThat(hasIntents || hasRejections)
        .as(
            "Transformer config for ValueType %s should have at least one intent or rejection configured",
            config.valueType())
        .isTrue();
  }

  /** Verifies that all transformer configs have a valid ValueType. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("allTransformerConfigs")
  void shouldHaveValidValueType(final TransformerConfig config) {
    assertThat(config.valueType())
        .as("Transformer config should have a non-null ValueType")
        .isNotNull();
  }

  /** Verifies that if a transformer config has rejections, it also has rejection types. */
  @ParameterizedTest(name = "{0}")
  @MethodSource("allTransformerConfigs")
  void shouldHaveRejectionTypesWhenRejectionsAreConfigured(final TransformerConfig config) {
    if (!config.supportedRejections().isEmpty()) {
      assertThat(config.supportedRejectionTypes())
          .as(
              "Transformer config for ValueType %s has rejections but no rejection types configured",
              config.valueType())
          .isNotEmpty();
    }
  }

  /** Tests that all predefined configs are used by at least one transformer. */
  @Test
  void shouldHaveAllPredefinedConfigsUsedByTransformers() {
    final List<TransformerConfig> predefinedConfigs = getAllTransformerConfigs();
    final List<AuditLogTransformer<?>> transformers = getAllTransformers();

    final Set<TransformerConfig> usedConfigs =
        transformers.stream().map(AuditLogTransformer::config).collect(Collectors.toSet());

    for (final TransformerConfig config : predefinedConfigs) {
      assertThat(usedConfigs)
          .as(
              "Predefined config for ValueType %s should be used by at least one transformer",
              config.valueType())
          .contains(config);
    }
  }

  private static Stream<Arguments> allTransformerConfigs() {
    return getAllTransformerConfigs().stream()
        .map(config -> Arguments.of(Named.of(getConfigName(config), config)));
  }

  private static List<TransformerConfig> getAllTransformerConfigs() {
    final List<TransformerConfig> configs = new ArrayList<>();

    for (final Field field : AuditLogTransformerConfigs.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isPublic(field.getModifiers())
          && TransformerConfig.class.isAssignableFrom(field.getType())) {
        try {
          final TransformerConfig config = (TransformerConfig) field.get(null);
          configs.add(config);
        } catch (final IllegalAccessException e) {
          throw new RuntimeException(
              "Failed to access field " + field.getName() + " in AuditLogTransformerConfigs", e);
        }
      }
    }

    return configs;
  }

  private static List<AuditLogTransformer<?>> getAllTransformers() {
    return AuditLogTransformerRegistry.createAllTransformers();
  }

  private static String getConfigName(final TransformerConfig config) {
    // Find the field name in AuditLogTransformerConfigs that matches this config
    for (final Field field : AuditLogTransformerConfigs.class.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())
          && Modifier.isPublic(field.getModifiers())
          && TransformerConfig.class.isAssignableFrom(field.getType())) {
        try {
          if (field.get(null) == config) {
            return field.getName();
          }
        } catch (final IllegalAccessException e) {
          // ignore
        }
      }
    }
    return config.valueType().name();
  }

  private static Stream<Arguments> entityTypeToRelatedEntityTypeProvider() {
    return Stream.of(
        Arguments.of(EntityType.USER, AuditLogEntityType.USER),
        Arguments.of(EntityType.GROUP, AuditLogEntityType.GROUP),
        Arguments.of(EntityType.ROLE, AuditLogEntityType.ROLE),
        Arguments.of(EntityType.MAPPING_RULE, AuditLogEntityType.MAPPING_RULE),
        Arguments.of(EntityType.UNSPECIFIED, null),
        Arguments.of(null, null));
  }

  @MethodSource("entityTypeToRelatedEntityTypeProvider")
  @ParameterizedTest
  void shouldMapOwnerTypeToEntityType(
      final EntityType ownerType, final AuditLogEntityType expectedEntityType) {
    final var entityType = AuditLogTransformerConfigs.mapEntityTypeToAuditLogEntityType(ownerType);
    assertThat(entityType).isEqualTo(expectedEntityType);
  }
}
