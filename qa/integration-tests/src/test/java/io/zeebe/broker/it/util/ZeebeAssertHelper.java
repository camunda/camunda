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
package io.zeebe.broker.it.util;

import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_ACTIVATING;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_COMPLETED;
import static io.zeebe.protocol.intent.WorkflowInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.VariableDocumentRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.protocol.BpmnElementType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.WorkflowInstanceRecordStream;
import java.util.function.Consumer;

public class ZeebeAssertHelper {

  public static void assertWorkflowInstanceCreated() {
    assertWorkflowInstanceCreated((e) -> {});
  }

  public static void assertWorkflowInstanceCreated(long workflowInstanceKey) {
    assertWorkflowInstanceCreated(workflowInstanceKey, w -> {});
  }

  public static void assertWorkflowInstanceCreated(Consumer<WorkflowInstanceRecordValue> consumer) {
    assertWorkflowInstanceState(WorkflowInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertJobCreated(String jobType) {
    assertThat(RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists()).isTrue();
  }

  public static void assertJobCreated(String jobType, Consumer<JobRecordValue> consumer) {
    final JobRecordValue value =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst().getValue();

    assertThat(value).isNotNull();

    consumer.accept(value);
  }

  public static void assertIncidentCreated() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.CREATED).exists()).isTrue();
  }

  public static void assertWorkflowInstanceCompleted(
      long workflowInstanceKey, Consumer<WorkflowInstanceRecordValue> consumer) {
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(ELEMENT_COMPLETED)
            .withRecordKey(workflowInstanceKey)
            .getFirst();

    assertThat(record).isNotNull();

    if (consumer != null) {
      consumer.accept(record.getValue());
    }
  }

  public static void assertWorkflowInstanceCompleted(long workflowInstanceKey) {
    assertWorkflowInstanceCompleted(workflowInstanceKey, r -> {});
  }

  public static void assertElementActivated(String element) {
    assertElementInState(ELEMENT_ACTIVATED, element, (e) -> {});
  }

  public static void assertElementReady(String element) {
    assertElementInState(ELEMENT_ACTIVATING, element, (e) -> {});
  }

  public static void assertWorkflowInstanceCanceled(String bpmnId) {
    assertThat(
            RecordingExporter.workflowInstanceRecords(ELEMENT_TERMINATED)
                .withBpmnProcessId(bpmnId)
                .withElementId(bpmnId)
                .exists())
        .isTrue();
  }

  public static void assertWorkflowInstanceCompleted(String workflow, long workflowInstanceKey) {
    assertElementCompleted(workflowInstanceKey, workflow, (e) -> {});
  }

  public static void assertWorkflowInstanceCompleted(String bpmnId) {
    assertWorkflowInstanceCompleted(bpmnId, (e) -> {});
  }

  public static void assertWorkflowInstanceCompleted(
      String bpmnId, Consumer<WorkflowInstanceRecordValue> eventConsumer) {
    assertElementCompleted(bpmnId, bpmnId, eventConsumer);
  }

  public static void assertJobCompleted() {
    assertThat(RecordingExporter.jobRecords(JobIntent.COMPLETED).exists()).isTrue();
  }

  public static void assertJobCanceled() {
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).exists()).isTrue();
  }

  public static void assertJobCompleted(String jobType) {
    assertJobCompleted(jobType, (j) -> {});
  }

  public static void assertJobCompleted(String jobType, Consumer<JobRecordValue> consumer) {
    final JobRecordValue job =
        RecordingExporter.jobRecords(JobIntent.COMPLETED).withType(jobType).getFirst().getValue();

    assertThat(job).isNotNull();
    consumer.accept(job);
  }

  public static void assertElementCompleted(String bpmnId, String activity) {
    assertElementCompleted(bpmnId, activity, (e) -> {});
  }

  public static void assertElementCompleted(
      String bpmnId, String activity, Consumer<WorkflowInstanceRecordValue> eventConsumer) {
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecordValueRecord =
        RecordingExporter.workflowInstanceRecords(ELEMENT_COMPLETED)
            .withBpmnProcessId(bpmnId)
            .withElementId(activity)
            .getFirst();

    assertThat(workflowInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(workflowInstanceRecordValueRecord.getValue());
  }

  public static void assertElementCompleted(
      long workflowInstanceKey,
      String activity,
      Consumer<WorkflowInstanceRecordValue> eventConsumer) {
    final Record<WorkflowInstanceRecordValue> workflowInstanceRecordValueRecord =
        RecordingExporter.workflowInstanceRecords(ELEMENT_COMPLETED)
            .withElementId(activity)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .getFirst();

    assertThat(workflowInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(workflowInstanceRecordValueRecord.getValue());
  }

  public static void assertWorkflowInstanceState(
      long workflowInstanceKey,
      WorkflowInstanceIntent intent,
      Consumer<WorkflowInstanceRecordValue> consumer) {
    consumeFirstWorkflowInstanceRecord(
        RecordingExporter.workflowInstanceRecords(intent)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .filter(r -> r.getKey() == r.getValue().getWorkflowInstanceKey()),
        consumer);
  }

  public static void assertWorkflowInstanceCreated(
      long workflowInstanceKey, Consumer<WorkflowInstanceRecordValue> consumer) {
    assertWorkflowInstanceState(
        workflowInstanceKey, WorkflowInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertWorkflowInstanceState(
      WorkflowInstanceIntent intent, Consumer<WorkflowInstanceRecordValue> consumer) {
    consumeFirstWorkflowInstanceRecord(
        RecordingExporter.workflowInstanceRecords(intent)
            .filter(r -> r.getKey() == r.getValue().getWorkflowInstanceKey()),
        consumer);
  }

  public static void assertElementInState(
      long workflowInstanceKey, String elementId, WorkflowInstanceIntent intent) {
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(intent)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementId(elementId)
            .getFirst();

    assertThat(record).isNotNull();
  }

  public static void assertElementInState(
      long workflowInstanceKey,
      String elementId,
      BpmnElementType elementType,
      WorkflowInstanceIntent intent) {
    final Record<WorkflowInstanceRecordValue> record =
        RecordingExporter.workflowInstanceRecords(intent)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .withElementType(elementType)
            .withElementId(elementId)
            .getFirst();

    assertThat(record).isNotNull();
  }

  public static void assertElementInState(
      WorkflowInstanceIntent intent,
      String element,
      Consumer<WorkflowInstanceRecordValue> consumer) {
    consumeFirstWorkflowInstanceRecord(
        RecordingExporter.workflowInstanceRecords(intent).withElementId(element), consumer);
  }

  private static void consumeFirstWorkflowInstanceRecord(
      WorkflowInstanceRecordStream stream, Consumer<WorkflowInstanceRecordValue> consumer) {

    final WorkflowInstanceRecordValue value = stream.getFirst().getValue();

    assertThat(value).isNotNull();

    consumer.accept(value);
  }

  public static void assertIncidentResolved() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).exists()).isTrue();
  }

  public static void assertIncidentResolveFailed() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.RESOLVED).exists()).isTrue();

    assertThat(
            RecordingExporter.incidentRecords()
                .skipUntil(e -> e.getMetadata().getIntent() == IncidentIntent.RESOLVED)
                .filter(e -> e.getMetadata().getIntent() == IncidentIntent.CREATED)
                .exists())
        .isTrue();
  }

  public static void assertVariableDocumentUpdated() {
    assertVariableDocumentUpdated(e -> {});
  }

  public static void assertVariableDocumentUpdated(
      Consumer<VariableDocumentRecordValue> eventConsumer) {
    final Record<VariableDocumentRecordValue> record =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED).getFirst();

    assertThat(record).isNotNull();
    eventConsumer.accept(record.getValue());
  }
}
