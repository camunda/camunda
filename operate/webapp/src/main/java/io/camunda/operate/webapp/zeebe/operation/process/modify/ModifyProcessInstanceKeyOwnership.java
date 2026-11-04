/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.ADD_VARIABLE;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.CANCEL_TOKEN;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.EDIT_VARIABLE;
import static io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type.MOVE_TOKEN;

import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.util.StringUtils;

public final class ModifyProcessInstanceKeyOwnership {

  private ModifyProcessInstanceKeyOwnership() {}

  /**
   * Returns whether the key is the process instance itself or one of its flow node instances. The
   * process instance itself needs no lookup.
   */
  public static boolean belongsToProcessInstance(
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final Long processInstanceKey,
      final Long key) {
    return findForeignKeys(flowNodeInstanceReader, processInstanceKey, Set.of(key)).isEmpty();
  }

  /**
   * Returns the keys that are neither the process instance itself nor one of its flow node
   * instances. The process instance itself needs no lookup.
   */
  private static Set<Long> findForeignKeys(
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final Long processInstanceKey,
      final Set<Long> keys) {
    final Set<Long> keysToLookUp = new HashSet<>(keys);
    keysToLookUp.remove(processInstanceKey);
    if (keysToLookUp.isEmpty()) {
      return Set.of();
    }
    final Set<Long> foreignKeys = new HashSet<>(keysToLookUp);
    foreignKeys.removeAll(
        flowNodeInstanceReader.findFlowNodeInstanceKeysOwnedBy(processInstanceKey, keysToLookUp));
    return foreignKeys;
  }

  /**
   * Returns an error message for the first key of the modifications that does not belong to the
   * process instance: the scope of a variable modification (the process instance itself when no
   * scope key is given), the ancestor element instance key, and the source flow node instance key
   * of a cancel or move token modification.
   */
  public static Optional<String> findViolation(
      final FlowNodeInstanceReader flowNodeInstanceReader,
      final Long processInstanceKey,
      final List<Modification> modifications) {
    record KeyToCheck(String description, Long key) {}

    final List<KeyToCheck> keysToCheck = new ArrayList<>();
    for (final Modification modification : modifications) {
      final Modification.Type type = modification.getModification();
      if (type == ADD_VARIABLE || type == EDIT_VARIABLE) {
        keysToCheck.add(
            new KeyToCheck(
                "Scope",
                modification.getScopeKey() == null
                    ? processInstanceKey
                    : modification.getScopeKey()));
      }
      if (modification.getAncestorElementInstanceKey() != null) {
        keysToCheck.add(
            new KeyToCheck("Element instance", modification.getAncestorElementInstanceKey()));
      }
      if ((type == CANCEL_TOKEN || type == MOVE_TOKEN)
          && StringUtils.hasText(modification.getFromFlowNodeInstanceKey())) {
        keysToCheck.add(
            new KeyToCheck(
                "Element instance", Long.parseLong(modification.getFromFlowNodeInstanceKey())));
      }
    }

    final Set<Long> foreignKeys =
        findForeignKeys(
            flowNodeInstanceReader,
            processInstanceKey,
            keysToCheck.stream().map(KeyToCheck::key).collect(Collectors.toSet()));
    return keysToCheck.stream()
        .filter(keyToCheck -> foreignKeys.contains(keyToCheck.key()))
        .findFirst()
        .map(
            keyToCheck ->
                String.format(
                    "%s %s does not belong to process instance %s.",
                    keyToCheck.description(), keyToCheck.key(), processInstanceKey));
  }
}
