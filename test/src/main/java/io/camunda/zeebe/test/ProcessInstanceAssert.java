/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test;

import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.impl.ZeebeObjectMapper;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.stream.StreamWrapperException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.assertj.core.api.AbstractAssert;

/**
 * @deprecated since 1.3.0. See issue <a
 *     href="https://github.com/camunda-cloud/zeebe/issues/8143">8143</a> for more information.
 */
@Deprecated(since = "1.3.0", forRemoval = true)
public class ProcessInstanceAssert
    extends AbstractAssert<ProcessInstanceAssert, ProcessInstanceEvent> {
  private static final ZeebeObjectMapper OBJECT_MAPPER = new ZeebeObjectMapper();

  private static final List<ProcessInstanceIntent> ELEMENT_PASSED_INTENTS =
      Arrays.asList(
          ProcessInstanceIntent.ELEMENT_COMPLETED, ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN);

  private static final List<ProcessInstanceIntent> INSTANCE_ENDED_INTENTS =
      Arrays.asList(
          ProcessInstanceIntent.ELEMENT_COMPLETED, ProcessInstanceIntent.ELEMENT_TERMINATED);

  private final long processInstanceKey;

  public ProcessInstanceAssert(final ProcessInstanceEvent actual) {
    super(actual, ProcessInstanceAssert.class);

    processInstanceKey = actual.getProcessInstanceKey();
  }

  public static ProcessInstanceAssert assertThat(final ProcessInstanceEvent actual) {
    return new ProcessInstanceAssert(actual);
  }

  public ProcessInstanceAssert isEnded() {

    final boolean isEnded =
        exists(
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(processInstanceKey)
                .withRecordKey(processInstanceKey)
                .filter(intent(INSTANCE_ENDED_INTENTS)));

    if (!isEnded) {
      failWithMessage("Expected process instance to be <ended> but was <active>");
    }

    return this;
  }

  public ProcessInstanceAssert hasPassed(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> passedElements =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
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

  public ProcessInstanceAssert hasEntered(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> enteredElements =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
            .withProcessInstanceKey(processInstanceKey)
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

  public ProcessInstanceAssert hasCompleted(final String... elementIds) {

    final List<String> ids = Arrays.asList(elementIds);

    final List<String> completedElements =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
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

  public ProcessInstanceAssert hasVariable(final String key, final Object expectedValue) {
    final Optional<Record<ProcessInstanceRecordValue>> record =
        RecordingExporter.processInstanceRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withRecordKey(processInstanceKey)
            .filter(intent(INSTANCE_ENDED_INTENTS))
            .findFirst();

    if (record.isPresent()) {
      hasVariable(record.get(), key, expectedValue);
    } else {
      failWithMessage("Expected process instance to contain variables but instance is not ended");
    }

    return this;
  }

  private ProcessInstanceAssert hasVariable(
      final Record<ProcessInstanceRecordValue> record,
      final String key,
      final Object expectedValue) {
    final Map<String, String> variables =
        ProcessInstances.getCurrentVariables(processInstanceKey, record.getPosition());

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

  private boolean exists(final ProcessInstanceRecordStream stream) {
    try {
      return stream.exists();
    } catch (final StreamWrapperException e) {
      return false;
    }
  }

  private static Predicate<Record<ProcessInstanceRecordValue>> intent(
      final List<ProcessInstanceIntent> intents) {
    return record -> intents.contains(record.getIntent());
  }

  private static Predicate<Record<ProcessInstanceRecordValue>> elementId(
      final List<String> elementIds) {
    return record -> {
      final String elementId = record.getValue().getElementId();

      return elementIds.contains(elementId);
    };
  }
}
