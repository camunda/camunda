/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.it.util;

import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_ACTIVATING;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_COMPLETED;
import static io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent.ELEMENT_TERMINATED;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstanceRecordStream;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.function.Consumer;

public final class ZeebeAssertHelper {

  public static void assertProcessInstanceCreated() {
    assertProcessInstanceCreated((e) -> {});
  }

  public static void assertProcessInstanceCreated(final long processInstanceKey) {
    assertProcessInstanceCreated(processInstanceKey, w -> {});
  }

  public static void assertProcessInstanceCreated(
      final Consumer<ProcessInstanceRecordValue> consumer) {
    assertProcessInstanceState(ProcessInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertJobCreated(final String jobType) {
    assertThat(RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists()).isTrue();
  }

  public static void assertJobCreated(
      final String jobType, final Consumer<JobRecordValue> consumer) {
    final JobRecordValue value =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withType(jobType)
            .findFirst()
            .map(Record::getValue)
            .orElse(null);

    assertThat(value).isNotNull();

    consumer.accept(value);
  }

  public static void assertIncidentCreated() {
    assertThat(RecordingExporter.incidentRecords(IncidentIntent.CREATED).exists()).isTrue();
  }

  public static void assertProcessInstanceCompleted(
      final long processInstanceKey, final Consumer<ProcessInstanceRecordValue> consumer) {
    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withRecordKey(processInstanceKey)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();

    if (consumer != null) {
      consumer.accept(record.getValue());
    }
  }

  public static void assertProcessInstanceCompleted(final long processInstanceKey) {
    assertProcessInstanceCompleted(processInstanceKey, r -> {});
  }

  public static void assertElementActivated(final String element) {
    assertElementInState(ELEMENT_ACTIVATED, element, (e) -> {});
  }

  public static void assertElementReady(final String element) {
    assertElementInState(ELEMENT_ACTIVATING, element, (e) -> {});
  }

  public static void assertProcessInstanceCanceled(final String bpmnId) {
    assertThat(
            RecordingExporter.processInstanceRecords(ELEMENT_TERMINATED)
                .withBpmnProcessId(bpmnId)
                .withElementId(bpmnId)
                .exists())
        .isTrue();
  }

  public static void assertProcessInstanceCompleted(
      final String process, final long processInstanceKey) {
    assertElementCompleted(processInstanceKey, process, (e) -> {});
  }

  public static void assertProcessInstanceCompleted(final String bpmnId) {
    assertProcessInstanceCompleted(bpmnId, (e) -> {});
  }

  public static void assertProcessInstanceCompleted(
      final String bpmnId, final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    assertElementCompleted(bpmnId, bpmnId, eventConsumer);
  }

  public static void assertJobCompleted() {
    assertThat(RecordingExporter.jobRecords(JobIntent.COMPLETED).exists()).isTrue();
  }

  public static void assertJobCanceled() {
    assertThat(RecordingExporter.jobRecords(JobIntent.CANCELED).exists()).isTrue();
  }

  public static void assertJobCompleted(final String jobType) {
    assertJobCompleted(jobType, (j) -> {});
  }

  public static void assertJobCompleted(
      final String jobType, final Consumer<JobRecordValue> consumer) {
    final JobRecordValue job =
        RecordingExporter.jobRecords(JobIntent.COMPLETED)
            .withType(jobType)
            .findFirst()
            .map(Record::getValue)
            .orElse(null);

    assertThat(job).isNotNull();
    consumer.accept(job);
  }

  public static void assertElementCompleted(final String bpmnId, final String activity) {
    assertElementCompleted(bpmnId, activity, (e) -> {});
  }

  public static void assertElementCompleted(
      final String bpmnId,
      final String activity,
      final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    final Record<ProcessInstanceRecordValue> processInstanceRecordValueRecord =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withBpmnProcessId(bpmnId)
            .withElementId(activity)
            .findFirst()
            .orElse(null);

    assertThat(processInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(processInstanceRecordValueRecord.getValue());
  }

  public static void assertElementCompleted(
      final long processInstanceKey,
      final String activity,
      final Consumer<ProcessInstanceRecordValue> eventConsumer) {
    final Record<ProcessInstanceRecordValue> processInstanceRecordValueRecord =
        RecordingExporter.processInstanceRecords(ELEMENT_COMPLETED)
            .withElementId(activity)
            .withProcessInstanceKey(processInstanceKey)
            .findFirst()
            .orElse(null);

    assertThat(processInstanceRecordValueRecord).isNotNull();

    eventConsumer.accept(processInstanceRecordValueRecord.getValue());
  }

  public static void assertProcessInstanceState(
      final long processInstanceKey,
      final ProcessInstanceIntent intent,
      final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .filter(r -> r.getKey() == r.getValue().getProcessInstanceKey()),
        consumer);
  }

  public static void assertProcessInstanceCreated(
      final long processInstanceKey, final Consumer<ProcessInstanceRecordValue> consumer) {
    assertProcessInstanceState(
        processInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, consumer);
  }

  public static void assertProcessInstanceState(
      final ProcessInstanceIntent intent, final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent)
            .filter(r -> r.getKey() == r.getValue().getProcessInstanceKey()),
        consumer);
  }

  public static void assertElementInState(
      final long processInstanceKey, final String elementId, final ProcessInstanceIntent intent) {
    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords(intent)
            .withProcessInstanceKey(processInstanceKey)
            .withElementId(elementId)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();
  }

  public static void assertElementInState(
      final long processInstanceKey,
      final String elementId,
      final BpmnElementType elementType,
      final ProcessInstanceIntent intent) {
    assertThat(
            RecordingExporter.processInstanceRecords(intent)
                .withProcessInstanceKey(processInstanceKey)
                .withElementType(elementType)
                .withElementId(elementId)
                .exists())
        .isTrue();
  }

  public static void assertElementInState(
      final ProcessInstanceIntent intent,
      final String element,
      final Consumer<ProcessInstanceRecordValue> consumer) {
    consumeFirstProcessInstanceRecord(
        RecordingExporter.processInstanceRecords(intent).withElementId(element), consumer);
  }

  private static void consumeFirstProcessInstanceRecord(
      final ProcessInstanceRecordStream stream,
      final Consumer<ProcessInstanceRecordValue> consumer) {

    final ProcessInstanceRecordValue value = stream.findFirst().map(Record::getValue).orElse(null);

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
                .skipUntil(e -> e.getIntent() == IncidentIntent.RESOLVED)
                .filter(e -> e.getIntent() == IncidentIntent.CREATED)
                .exists())
        .isTrue();
  }

  public static void assertVariableDocumentUpdated() {
    assertVariableDocumentUpdated(e -> {});
  }

  public static void assertVariableDocumentUpdated(
      final Consumer<VariableDocumentRecordValue> eventConsumer) {
    final Record<VariableDocumentRecordValue> record =
        RecordingExporter.variableDocumentRecords(VariableDocumentIntent.UPDATED)
            .findFirst()
            .orElse(null);

    assertThat(record).isNotNull();
    eventConsumer.accept(record.getValue());
  }
}
