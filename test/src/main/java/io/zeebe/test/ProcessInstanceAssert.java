/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.test;

import io.zeebe.client.api.response.WorkflowInstanceEvent;
import io.zeebe.client.impl.ZeebeObjectMapper;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.WorkflowInstanceRecordValue;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstanceRecordStream;
import io.zeebe.test.util.record.WorkflowInstances;
import io.zeebe.test.util.stream.StreamWrapperException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

public class WorkflowInstanceAssert
    extends AbstractAssert<WorkflowInstanceAssert, WorkflowInstanceEvent> {
  private static final ZeebeObjectMapper OBJECT_MAPPER = new ZeebeObjectMapper();

  private static final List<WorkflowInstanceIntent> ELEMENT_PASSED_INTENTS =
      Arrays.asList(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

  private static final List<WorkflowInstanceIntent> INSTANCE_ENDED_INTENTS =
      Arrays.asList(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, WorkflowInstanceIntent.ELEMENT_TERMINATED);

  private final long workflowInstanceKey;

  public WorkflowInstanceAssert(final WorkflowInstanceEvent actual) {
    super(actual, WorkflowInstanceAssert.class);

    workflowInstanceKey = actual.getWorkflowInstanceKey();
  }

  public static WorkflowInstanceAssert assertThat(final WorkflowInstanceEvent actual) {
    return new WorkflowInstanceAssert(actual);
  }

  public WorkflowInstanceAssert isEnded() {

    final boolean isEnded =
        exists(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withRecordKey(workflowInstanceKey)
                .filter(intent(INSTANCE_ENDED_INTENTS)));

    if (!isEnded) {
      failWithMessage("Expected workflow instance to be <ended> but was <active>");
    }

    return this;
  }

  public WorkflowInstanceAssert hasPassed(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> passedElements =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filter(intent(ELEMENT_PASSED_INTENTS))
            .filter(elementId(ids))
            .map(r -> r.getValue().getElementId())
            .limit(ids.size())
            .collect(Collectors.toList());

    if (passedElements.size() < ids.size()) {
      final List<String> notPassed = new ArrayList<>(ids);
      notPassed.removeAll(passedElements);

      failWithMessage("Expected <%s> to be passed but could not find <%s>", ids, notPassed);
    }

    return this;
  }

  public WorkflowInstanceAssert hasEntered(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> enteredElements =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filter(elementId(ids))
            .map(r -> r.getValue().getElementId())
            .limit(ids.size())
            .collect(Collectors.toList());

    if (enteredElements.size() < ids.size()) {
      final List<String> notEntered = new ArrayList<>(ids);
      notEntered.removeAll(enteredElements);

      failWithMessage("Expected <%s> to be entered but could not find <%s>", ids, notEntered);
    }

    return this;
  }

  public WorkflowInstanceAssert hasCompleted(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> completedElements =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_COMPLETED)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filter(elementId(ids))
            .map(r -> r.getValue().getElementId())
            .limit(ids.size())
            .collect(Collectors.toList());

    if (completedElements.size() < ids.size()) {
      final List<String> notCompleted = new ArrayList<>(ids);
      notCompleted.removeAll(completedElements);

      failWithMessage("Expected <%s> to be completed but could not find <%s>", ids, notCompleted);
    }

    return this;
  }

  public WorkflowInstanceAssert hasVariable(final String key, final Object expectedValue) {
    final Optional<Record<WorkflowInstanceRecordValue>> record =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withRecordKey(workflowInstanceKey)
            .filter(intent(INSTANCE_ENDED_INTENTS))
            .findFirst();

    if (record.isPresent()) {
      hasVariable(record.get(), key, expectedValue);
    } else {
      failWithMessage("Expected workflow instance to contain variables but instance is not ended");
    }

    return this;
  }

  private WorkflowInstanceAssert hasVariable(
      final Record<WorkflowInstanceRecordValue> record,
      final String key,
      final Object expectedValue) {
    final Map<String, String> variables =
        WorkflowInstances.getCurrentVariables(workflowInstanceKey, record.getPosition());

    if (!variables.containsKey(key)) {
      failWithMessage(
          "Expected variables <%s> to contain <%s> but could not find entry", variables, key);
      return this;
    }

    final Object value;
    value = OBJECT_MAPPER.fromJson(variables.get(key), Object.class);

    if ((expectedValue == null && value != null)
        || (expectedValue != null && !expectedValue.equals(value))) {
      failWithMessage(
          "Expected variables value of <%s> to be <%s> but was <%s>", key, expectedValue, value);
    }

    return this;
  }

  private boolean exists(final WorkflowInstanceRecordStream stream) {
    try {
      return stream.exists();
    } catch (final StreamWrapperException e) {
      return false;
    }
  }

  private static Predicate<Record<WorkflowInstanceRecordValue>> intent(
      final List<WorkflowInstanceIntent> intents) {
    return record -> intents.contains(record.getIntent());
  }

  private static Predicate<Record<WorkflowInstanceRecordValue>> elementId(
      final List<String> elementIds) {
    return record -> {
      final String elementId = record.getValue().getElementId();

      return elementIds.contains(elementId);
    };
  }
}
