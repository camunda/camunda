/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class RecordStream extends ExporterRecordStream<RecordValue, RecordStream> {
  public RecordStream(final Stream<Record<RecordValue>> wrappedStream) {
    super(wrappedStream);
  }

  @Override
  protected RecordStream supply(final Stream<Record<RecordValue>> wrappedStream) {
    return new RecordStream(wrappedStream);
  }

  public RecordStream between(final long lowerBoundPosition, final long upperBoundPosition) {
    return between(
        r -> r.getPosition() > lowerBoundPosition, r -> r.getPosition() >= upperBoundPosition);
  }

  public RecordStream between(final Record<?> lowerBound, final Record<?> upperBound) {
    return between(lowerBound::equals, upperBound::equals);
  }

  public RecordStream between(
      final Predicate<Record<?>> lowerBound, final Predicate<Record<?>> upperBound) {
    return supply(dropWhile(Predicate.not(lowerBound))).limit(upperBound::test);
  }

  public RecordStream after(final long lowerBoundPosition) {
    return supply(dropWhile(r -> r.getPosition() <= lowerBoundPosition));
  }

  public RecordStream limitToProcessInstance(final long processInstanceKey) {
    return limit(
        r ->
            r.getKey() == processInstanceKey
                && Set.of(
                        ProcessInstanceIntent.ELEMENT_COMPLETED,
                        ProcessInstanceIntent.ELEMENT_TERMINATED)
                    .contains(r.getIntent()));
  }

  public RecordStream betweenProcessInstance(final long processInstanceKey) {
    return between(
        r ->
            r.getKey() == processInstanceKey
                && r.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATING,
        r ->
            r.getKey() == processInstanceKey
                && Set.of(
                        ProcessInstanceIntent.ELEMENT_COMPLETED,
                        ProcessInstanceIntent.ELEMENT_TERMINATED)
                    .contains(r.getIntent()));
  }

  public ProcessRecordStream processRecords() {
    return new ProcessRecordStream(
        filter(r -> r.getValueType() == ValueType.PROCESS).map(Record.class::cast));
  }

  public ProcessInstanceRecordStream processInstanceRecords() {
    return new ProcessInstanceRecordStream(
        filter(r -> r.getValueType() == ValueType.PROCESS_INSTANCE).map(Record.class::cast));
  }

  public TimerRecordStream timerRecords() {
    return new TimerRecordStream(
        filter(r -> r.getValueType() == ValueType.TIMER).map(Record.class::cast));
  }

  public VariableDocumentRecordStream variableDocumentRecords() {
    return new VariableDocumentRecordStream(
        filter(r -> r.getValueType() == ValueType.VARIABLE_DOCUMENT).map(Record.class::cast));
  }

  public VariableRecordStream variableRecords() {
    return new VariableRecordStream(
        filter(r -> r.getValueType() == ValueType.VARIABLE).map(Record.class::cast));
  }

  public JobRecordStream jobRecords() {
    return new JobRecordStream(
        filter(r -> r.getValueType() == ValueType.JOB).map(Record.class::cast));
  }

  public FormRecordStream formRecords() {
    return new FormRecordStream(
        filter(r -> r.getValueType() == ValueType.FORM).map(Record.class::cast));
  }

  public IncidentRecordStream incidentRecords() {
    return new IncidentRecordStream(
        filter(r -> r.getValueType() == ValueType.INCIDENT).map(Record.class::cast));
  }

  public MessageRecordStream messageRecords() {
    return new MessageRecordStream(
        filter(r -> r.getValueType() == ValueType.MESSAGE).map(Record.class::cast));
  }

  public MessageCorrelationRecordStream messageCorrelationRecords() {
    return new MessageCorrelationRecordStream(
        filter(r -> r.getValueType() == ValueType.MESSAGE_CORRELATION).map(Record.class::cast));
  }

  public MessageSubscriptionRecordStream messageSubscriptionRecords() {
    return new MessageSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.MESSAGE_SUBSCRIPTION).map(Record.class::cast));
  }

  public ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords() {
    return new ProcessMessageSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.PROCESS_MESSAGE_SUBSCRIPTION)
            .map(Record.class::cast));
  }

  public MessageStartEventSubscriptionRecordStream messageStartEventSubscriptionRecords() {
    return new MessageStartEventSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.MESSAGE_START_EVENT_SUBSCRIPTION)
            .map(Record.class::cast));
  }

  public SignalSubscriptionRecordStream signalSubscriptionRecords() {
    return new SignalSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.SIGNAL_SUBSCRIPTION).map(Record.class::cast));
  }

  public SignalRecordStream signalRecords() {
    return new SignalRecordStream(
        filter(r -> r.getValueType() == ValueType.SIGNAL).map(Record.class::cast));
  }

  public UserTaskRecordStream userTaskRecords() {
    return new UserTaskRecordStream(
        filter(r -> r.getValueType() == ValueType.USER_TASK).map(Record.class::cast));
  }

  public UserRecordStream userRecords() {
    return new UserRecordStream(
        filter(r -> r.getValueType() == ValueType.USER).map(Record.class::cast));
  }

  public IdentitySetupRecordStream identitySetupRecords() {
    return new IdentitySetupRecordStream(
        filter(r -> r.getValueType() == ValueType.IDENTITY_SETUP).map(Record.class::cast));
  }

  public RoleRecordStream roleRecords() {
    return new RoleRecordStream(
        filter(r -> r.getValueType() == ValueType.ROLE).map(Record.class::cast));
  }

  public AuthorizationRecordStream authorizationRecords() {
    return new AuthorizationRecordStream(
        filter(r -> r.getValueType() == ValueType.AUTHORIZATION).map(Record.class::cast));
  }

  public ResourceDeletionRecordStream resourceDeletionRecords() {
    return new ResourceDeletionRecordStream(
        filter(r -> r.getValueType() == ValueType.RESOURCE_DELETION).map(Record.class::cast));
  }

  public AsyncRequestRecordStream asyncRequestRecords() {
    return new AsyncRequestRecordStream(
        filter(r -> r.getValueType() == ValueType.ASYNC_REQUEST).map(Record.class::cast));
  }

  public ConditionalSubscriptionRecordStream conditionalSubscriptionRecords() {
    return new ConditionalSubscriptionRecordStream(
        filter(r -> r.getValueType() == ValueType.CONDITIONAL_SUBSCRIPTION)
            .map(Record.class::cast));
  }

  public GlobalListenerBatchRecordStream globalListenerBatchRecords() {
    return new GlobalListenerBatchRecordStream(
        filter(r -> r.getValueType() == ValueType.GLOBAL_LISTENER_BATCH).map(Record.class::cast));
  }

  public GlobalListenerRecordStream globalListenerRecords() {
    return new GlobalListenerRecordStream(
        filter(r -> r.getValueType() == ValueType.GLOBAL_LISTENER).map(Record.class::cast));
  }

  public BatchOperationCreationRecordStream batchOperationCreationRecords() {
    return new BatchOperationCreationRecordStream(
        filter(r -> r.getValueType() == ValueType.BATCH_OPERATION_CREATION)
            .map(Record.class::cast));
  }

  public HistoryDeletionRecordStream historyDeletionRecords() {
    return new HistoryDeletionRecordStream(
        filter(r -> r.getValueType() == ValueType.HISTORY_DELETION).map(Record.class::cast));
  }

  public BatchOperationExecutionRecordStream batchOperationExecutionRecords() {
    return new BatchOperationExecutionRecordStream(
        filter(r -> r.getValueType() == ValueType.BATCH_OPERATION_EXECUTION)
            .map(Record.class::cast));
  }

  public ScaleRecordStream scaleRecords() {
    return new ScaleRecordStream(
        filter(r -> r.getValueType() == ValueType.SCALE).map(Record.class::cast));
  }
}
