/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import java.util.List;

/**
 * Utility class for resolving the root process instance key from various contexts.
 *
 * <p>The root process instance is the topmost process instance in the hierarchy. For top-level
 * process instances, this is the process instance itself. For child process instances (created via
 * call activities), this is the key of the topmost parent process instance.
 */
public final class RootProcessInstanceKeyResolver {

  private RootProcessInstanceKeyResolver() {
    // utility class
  }

  /**
   * Derives the root process instance key from the element instance path.
   *
   * <p>The element instance path is a multi-list structure where each list represents the hierarchy
   * within a process instance. The first list contains the root process instance and its nested
   * elements. The first element in the first list is always the root process instance key.
   *
   * @param elementInstancePath the hierarchical path of element instances
   * @return the key of the root process instance, or -1 if the path is empty
   */
  public static long fromElementInstancePath(final List<List<Long>> elementInstancePath) {
    if (elementInstancePath == null
        || elementInstancePath.isEmpty()
        || elementInstancePath.getFirst().isEmpty()) {
      return -1L;
    }
    return elementInstancePath.getFirst().getFirst();
  }

  /**
   * Resolves the root process instance key by traversing up the process instance hierarchy.
   *
   * <p>This method starts from the given process instance key and traverses up through parent
   * process instances until it reaches the root (where parentProcessInstanceKey is -1).
   *
   * @param elementInstanceState the state to query for element instances
   * @param processInstanceKey the key of the process instance to start from
   * @return the key of the root process instance, or -1 if the process instance is not found
   */
  public static long fromProcessInstanceKey(
      final ElementInstanceState elementInstanceState, final long processInstanceKey) {
    if (processInstanceKey == -1L) {
      return -1L;
    }

    final ElementInstance instance = elementInstanceState.getInstance(processInstanceKey);
    if (instance == null) {
      return -1L;
    }

    final var value = instance.getValue();
    final long parentProcessInstanceKey = value.getParentProcessInstanceKey();

    // If there's no parent, this is the root
    if (parentProcessInstanceKey == -1L) {
      return processInstanceKey;
    }

    // Otherwise, recursively traverse up to find the root
    return fromProcessInstanceKey(elementInstanceState, parentProcessInstanceKey);
  }
}
