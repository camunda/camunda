/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire value of every analytics event name.
 *
 * <p>Event names are a published contract: the analytics backend keys on {@code event.name}, so
 * renaming one splits the series for every existing record. The rest of the suite asserts against
 * the {@link AnalyticsAttributes.Event} constants rather than their values, which means a rename
 * would otherwise pass unnoticed.
 *
 * <p>If this test fails you have changed or added a published event name. That is allowed, but it
 * is a contract change: update the expectations here, the README event table, and coordinate the
 * migration with the analytics backend before merging.
 */
final class AnalyticsEventNamesTest {

  private static final Map<String, String> EXPECTED_EVENT_NAMES =
      Map.ofEntries(
          Map.entry("PROCESS_INSTANCE_ACTIVATED", "camunda.process.instance.activated"),
          Map.entry("HEARTBEAT", "camunda.telemetry.heartbeat"),
          Map.entry("USER_TASK_CREATED", "camunda.user_task.created"),
          Map.entry("USER_TASK_ASSIGNED", "camunda.user_task.assigned"),
          Map.entry("TENANT_CREATED", "camunda.tenant.created"),
          Map.entry("TENANT_DELETED", "camunda.tenant.deleted"),
          Map.entry("PROCESS_INCIDENT_CREATED", "camunda.process.incident.created"),
          Map.entry("PROCESS_INCIDENT_RESOLVED", "camunda.process.incident.resolved"),
          Map.entry("PROCESS_DEFINITION_CREATED", "camunda.process.definition.created"),
          Map.entry("PROCESS_DEFINITION_DELETED", "camunda.process.definition.deleted"),
          Map.entry("DECISION_DEFINITION_CREATED", "camunda.decision.definition.created"),
          Map.entry("DECISION_DEFINITION_DELETED", "camunda.decision.definition.deleted"),
          Map.entry("FORM_DEFINITION_CREATED", "camunda.form.definition.created"),
          Map.entry("FORM_DEFINITION_DELETED", "camunda.form.definition.deleted"),
          Map.entry("AGENT_INSTANCE_CREATED", "camunda.agent.instance.created"),
          Map.entry("AGENT_INSTANCE_COMPLETED", "camunda.agent.instance.completed"));

  @Test
  void shouldNotChangePublishedEventNames() throws IllegalAccessException {
    // when
    final Map<String, String> declared = declaredEventNames();

    // then
    assertThat(declared).containsExactlyInAnyOrderEntriesOf(EXPECTED_EVENT_NAMES);
  }

  private static Map<String, String> declaredEventNames() throws IllegalAccessException {
    final Map<String, String> names = new TreeMap<>();
    for (final Field field : AnalyticsAttributes.Event.class.getDeclaredFields()) {
      final int modifiers = field.getModifiers();
      if (field.getType() == String.class
          && Modifier.isPublic(modifiers)
          && Modifier.isStatic(modifiers)) {
        names.put(field.getName(), (String) field.get(null));
      }
    }
    return names;
  }
}
