/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.zeebe.test;

import io.zeebe.client.api.events.WorkflowInstanceEvent;
import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstanceRecordStream;
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

  private static final List<WorkflowInstanceIntent> ELEMENT_PASSED_INTENTS =
      Arrays.asList(
          WorkflowInstanceIntent.ELEMENT_COMPLETED,
          WorkflowInstanceIntent.EVENT_TRIGGERED,
          WorkflowInstanceIntent.EVENT_ACTIVATED,
          WorkflowInstanceIntent.GATEWAY_ACTIVATED,
          WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

  private static final List<WorkflowInstanceIntent> INSTANCE_ENDED_INTENTS =
      Arrays.asList(
          WorkflowInstanceIntent.ELEMENT_COMPLETED, WorkflowInstanceIntent.ELEMENT_TERMINATED);

  private final long workflowInstanceKey;

  public WorkflowInstanceAssert(WorkflowInstanceEvent actual) {
    super(actual, WorkflowInstanceAssert.class);

    workflowInstanceKey = actual.getWorkflowInstanceKey();
  }

  public static WorkflowInstanceAssert assertThat(WorkflowInstanceEvent actual) {
    return new WorkflowInstanceAssert(actual);
  }

  public WorkflowInstanceAssert isEnded() {

    final boolean isEnded =
        exists(
            RecordingExporter.workflowInstanceRecords()
                .withWorkflowInstanceKey(workflowInstanceKey)
                .withKey(workflowInstanceKey)
                .filter(intent(INSTANCE_ENDED_INTENTS)));

    if (!isEnded) {
      failWithMessage("Expected workflow instance to be <ended> but was <active>");
    }

    return this;
  }

  public WorkflowInstanceAssert hasPassed(String... elementIds) {

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

  public WorkflowInstanceAssert hasEntered(String... elementIds) {

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

  public WorkflowInstanceAssert hasCompleted(String... elementIds) {

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

  public WorkflowInstanceAssert hasElementPayload(
      String elementId, String key, Object expectedValue) {

    final Optional<Record<WorkflowInstanceRecordValue>> record =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .filter(r -> ELEMENT_PASSED_INTENTS.contains(r.getMetadata().getIntent()))
            .findFirst();

    if (record.isPresent()) {
      hasPayload(record.get(), key, expectedValue);

    } else {
      failWithMessage("Expected <%s> to contain payload but element was not passed", elementId);
    }

    return this;
  }

  public WorkflowInstanceAssert hasPayload(String key, Object expectedValue) {

    final Optional<Record<WorkflowInstanceRecordValue>> record =
        RecordingExporter.workflowInstanceRecords()
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withKey(workflowInstanceKey)
            .filter(intent(INSTANCE_ENDED_INTENTS))
            .findFirst();

    if (record.isPresent()) {
      hasPayload(record.get(), key, expectedValue);

    } else {
      failWithMessage("Expected workflow instance to contain payload but instance is not ended");
    }

    return this;
  }

  private WorkflowInstanceAssert hasPayload(
      final Record<WorkflowInstanceRecordValue> record, String key, Object expectedValue) {
    final Map<String, Object> payload = record.getValue().getPayloadAsMap();

    if (payload.containsKey(key)) {
      final Object value = payload.get(key);

      if (!expectedValue.equals(value)) {
        failWithMessage(
            "Expected payload value of <%s> to be <%s> but was <%s>", key, expectedValue, value);
      }

    } else {
      failWithMessage(
          "Expected payload <%s> to contain <%s> but could not find entry", payload, key);
    }

    return this;
  }

  private boolean exists(WorkflowInstanceRecordStream stream) {
    try {
      return stream.exists();
    } catch (StreamWrapperException e) {
      return false;
    }
  }

  private static Predicate<Record<WorkflowInstanceRecordValue>> intent(
      List<WorkflowInstanceIntent> intents) {
    return record -> {
      final Intent intent = record.getMetadata().getIntent();

      return intents.contains(intent);
    };
  }

  private static Predicate<Record<WorkflowInstanceRecordValue>> elementId(List<String> elementIds) {
    return record -> {
      final String elementId = record.getValue().getElementId();

      return elementIds.contains(elementId);
    };
  }
}
