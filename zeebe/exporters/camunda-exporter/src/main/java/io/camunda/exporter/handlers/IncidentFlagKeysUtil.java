/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.handlers;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Splits the incident flag keys of an incident record into process instance keys and flow node
 * instance keys. The engine decides the keys, the element instance path tells which kind a key is.
 * Only process instances, call activities and the incident element get the flag, the same set the
 * post importer used. Container elements on the path are skipped on purpose.
 */
final class IncidentFlagKeysUtil {

  private IncidentFlagKeysUtil() {}

  static List<Long> processInstanceFlagKeys(final IncidentRecordValue value) {
    final Set<Long> processInstanceKeys = processInstanceKeys(value);
    return value.getIncidentFlagKeys().stream().filter(processInstanceKeys::contains).toList();
  }

  static List<Long> flowNodeFlagKeys(final IncidentRecordValue value) {
    final Set<Long> flowNodeKeys =
        value.getElementInstancePath().stream()
            .filter(keys -> keys.size() > 1)
            .map(List::getLast)
            .collect(Collectors.toUnmodifiableSet());
    return value.getIncidentFlagKeys().stream().filter(flowNodeKeys::contains).toList();
  }

  static long processInstanceKeyOf(final IncidentRecordValue value, final long elementInstanceKey) {
    for (final List<Long> keysWithinOneProcessInstance : value.getElementInstancePath()) {
      if (keysWithinOneProcessInstance.contains(elementInstanceKey)) {
        return keysWithinOneProcessInstance.getFirst();
      }
    }
    throw new IllegalStateException(
        "Element instance key %d is not part of the element instance path %s"
            .formatted(elementInstanceKey, value.getElementInstancePath()));
  }

  static boolean isFlagIntent(final Record<IncidentRecordValue> record) {
    return record.getIntent() == IncidentIntent.CREATED
        || record.getIntent() == IncidentIntent.RESOLVED;
  }

  static boolean flagValue(final Record<IncidentRecordValue> record) {
    return record.getIntent() == IncidentIntent.CREATED;
  }

  private static Set<Long> processInstanceKeys(final IncidentRecordValue value) {
    return value.getElementInstancePath().stream()
        .filter(keys -> !keys.isEmpty())
        .map(List::getFirst)
        .collect(Collectors.toUnmodifiableSet());
  }
}
