/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire value of every analytics attribute key.
 *
 * <p>Attribute keys are a published contract: the analytics backend reads records by key, so
 * renaming one silently drops the value for every consumer that still reads the old key. The rest
 * of the suite asserts against the {@link AnalyticsAttributes} constants rather than their values,
 * which means a rename would otherwise pass unnoticed.
 *
 * <p>If this test fails you have changed or added a published attribute key. That is allowed, but
 * it is a contract change: update the expectations here, the README attribute tables, the data
 * contract in camunda/Holistic-Data-Platform (<a
 * href="https://github.com/camunda/Holistic-Data-Platform/tree/main/ingest/camunda-product-telemetry/schemas">{@code
 * ingest/camunda-product-telemetry/schemas/}</a>), and coordinate the migration with the analytics
 * backend before merging.
 */
final class AnalyticsAttributeKeysTest {

  private static final Map<String, String> EXPECTED_ATTRIBUTE_KEYS =
      Map.ofEntries(
          Map.entry("SERVICE_NAME", "service.name"),
          Map.entry("CLUSTER_ID", "camunda.cluster.id"),
          Map.entry("PARTITION_ID", "camunda.partition.id"),
          Map.entry("Event.NAME", "event.name"),
          Map.entry("Event.SEQUENCE_NUMBER", "camunda.event.sequence_number"),
          Map.entry("Event.SAMPLE_RATE", "camunda.event.sample_rate"),
          Map.entry("Event.TIME_MIN", "camunda.event.time_min"),
          Map.entry("Event.TIME_MAX", "camunda.event.time_max"),
          Map.entry("Log.POSITION", "camunda.log.position"),
          Map.entry("Log.POSITION_START", "camunda.log.position_start"),
          Map.entry("Log.POSITION_END", "camunda.log.position_end"),
          Map.entry("Tenant.ID", "camunda.tenant.id"),
          Map.entry("Tenant.PHYSICAL_ID", "camunda.tenant.physical_id"),
          Map.entry("Process.BPMN_PROCESS_ID", "camunda.process.id"),
          Map.entry("Process.VERSION", "camunda.process.definition.version"),
          Map.entry("Process.DEFINITION_KEY", "camunda.process.definition.key"),
          Map.entry("Process.INSTANCE_KEY", "camunda.process.instance.key"),
          Map.entry("Process.ROOT_INSTANCE_KEY", "camunda.process.root_instance.key"),
          Map.entry("Element.ID", "camunda.element.id"),
          Map.entry("UserTask.KEY", "camunda.user_task.key"),
          Map.entry("Incident.KEY", "camunda.incident.key"),
          Map.entry("Decision.ID", "camunda.decision.id"),
          Map.entry("Decision.KEY", "camunda.decision.definition.key"),
          Map.entry("Decision.VERSION", "camunda.decision.definition.version"),
          Map.entry("Form.ID", "camunda.form.id"),
          Map.entry("Form.KEY", "camunda.form.definition.key"),
          Map.entry("Form.VERSION", "camunda.form.definition.version"),
          Map.entry("Agent.INSTANCE_KEY", "camunda.agent.instance.key"),
          Map.entry("Agent.DEFINITION_KEY", "camunda.agent.definition.key"),
          Map.entry("Agent.STATUS", "camunda.agent.status"),
          Map.entry("Metric.SEQUENCE_NUMBER", "camunda.metric.sequence_number"),
          Map.entry("Heartbeat.BROKER_VERSION", "camunda.telemetry.heartbeat.broker_version"),
          Map.entry("Heartbeat.EXPORTER_VERSION", "camunda.telemetry.heartbeat.exporter_version"),
          Map.entry("Exporter.DIGEST", "camunda.exporter.digest"));

  @Test
  void shouldNotChangePublishedAttributeKeys() throws IllegalAccessException {
    // when
    final Map<String, String> declared = declaredAttributeKeys();

    // then
    assertThat(declared).containsExactlyInAnyOrderEntriesOf(EXPECTED_ATTRIBUTE_KEYS);
  }

  private static Map<String, String> declaredAttributeKeys() throws IllegalAccessException {
    final Map<String, String> keys = new TreeMap<>();
    collect(AnalyticsAttributes.class, "", keys);
    for (final Class<?> nested : AnalyticsAttributes.class.getDeclaredClasses()) {
      collect(nested, nested.getSimpleName() + ".", keys);
    }
    return keys;
  }

  private static void collect(
      final Class<?> type, final String prefix, final Map<String, String> keys)
      throws IllegalAccessException {
    for (final Field field : type.getDeclaredFields()) {
      final int modifiers = field.getModifiers();
      if (AttributeKey.class.isAssignableFrom(field.getType())
          && Modifier.isPublic(modifiers)
          && Modifier.isStatic(modifiers)) {
        keys.put(prefix + field.getName(), ((AttributeKey<?>) field.get(null)).getKey());
      }
    }
  }
}
