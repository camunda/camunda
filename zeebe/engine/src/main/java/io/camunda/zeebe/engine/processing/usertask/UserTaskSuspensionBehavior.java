/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.usertask;

import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.BufferedCommandRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.BufferedCommandIntent;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;

/**
 * Buffers one {@link UserTaskIntent#SUSPENDED} or {@link UserTaskIntent#RESUMED} entry per user
 * task of a process instance. The buffered command drain writes the event for one entry per cycle
 * (see {@code BufferedCommandDrainProcessor}).
 *
 * <p>User tasks have no index by process instance, so the keys are collected by walking the element
 * instance tree. User tasks of called child instances are left untouched, since {@link
 * ElementInstanceState#getChildren} never returns a child instance's root.
 */
@NullMarked
public final class UserTaskSuspensionBehavior {

  private final ElementInstanceState elementInstanceState;
  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;

  public UserTaskSuspensionBehavior(
      final ElementInstanceState elementInstanceState,
      final StateWriter stateWriter,
      final KeyGenerator keyGenerator) {
    this.elementInstanceState = elementInstanceState;
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
  }

  public List<Long> collectUserTaskKeys(final ElementInstance processInstance) {
    final long processInstanceKey = processInstance.getValue().getProcessInstanceKey();
    final var userTaskKeys = new ArrayList<Long>();
    // BFS to find all user tasks including nested scopes
    final var elementInstances = new ArrayDeque<ElementInstance>();
    elementInstances.add(processInstance);
    while (!elementInstances.isEmpty()) {
      final var elementInstance = elementInstances.poll();
      final long userTaskKey = elementInstance.getUserTaskKey();
      if (userTaskKey > 0) {
        userTaskKeys.add(userTaskKey);
      }
      // defensive, see class javadoc: getChildren never returns a child instance's root
      elementInstanceState.getChildren(elementInstance.getKey()).stream()
          .filter(child -> child.getValue().getProcessInstanceKey() == processInstanceKey)
          .forEach(elementInstances::add);
    }
    return userTaskKeys;
  }

  /** Buffers an entry with the given event intent for each of the user tasks. */
  public void bufferEvents(
      final ProcessInstanceRecord processInstance,
      final List<Long> userTaskKeys,
      final UserTaskIntent eventIntent) {
    userTaskKeys.forEach(
        userTaskKey ->
            stateWriter.appendFollowUpEvent(
                keyGenerator.nextKey(),
                BufferedCommandIntent.BUFFERED,
                new BufferedCommandRecord()
                    .setProcessInstanceKey(processInstance.getProcessInstanceKey())
                    .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
                    .setTenantId(processInstance.getTenantId())
                    .setCommandKey(userTaskKey)
                    .setValueType(ValueType.USER_TASK)
                    .setIntent(eventIntent)));
  }
}
