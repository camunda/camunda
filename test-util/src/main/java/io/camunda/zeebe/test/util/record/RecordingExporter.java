/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.test.util.record;

import io.camunda.zeebe.exporter.api.Exporter;
import io.camunda.zeebe.exporter.api.context.Controller;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordValue;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.JobBatchIntent;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.MessageIntent;
import io.camunda.zeebe.protocol.record.intent.MessageStartEventSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.MessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessMessageSubscriptionIntent;
import io.camunda.zeebe.protocol.record.intent.TimerIntent;
import io.camunda.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.DecisionEvaluationRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentRecordValue;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.JobBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.JobRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageStartEventSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.MessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceBatchRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceResultRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessMessageSubscriptionRecordValue;
import io.camunda.zeebe.protocol.record.value.TimerRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableDocumentRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.DecisionRequirementsRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public final class RecordingExporter implements Exporter {
  public static final long DEFAULT_MAX_WAIT_TIME = Duration.ofSeconds(5).toMillis();

  private static final List<Record<?>> RECORDS = new CopyOnWriteArrayList<>();
  private static final Lock LOCK = new ReentrantLock();
  private static final Condition IS_EMPTY = LOCK.newCondition();

  private static long maximumWaitTime = DEFAULT_MAX_WAIT_TIME;

  private Controller controller;

  public static void setMaximumWaitTime(final long maximumWaitTime) {
    RecordingExporter.maximumWaitTime = maximumWaitTime;
  }

  /**
   * Disables the default awaiting behavior of the recording exporter by setting the wait time to 0.
   *
   * <p>By default, the RecordingExporter awaits incoming records until all expected records are
   * retrieved (the expectation is controlled with limit), or a maximumWaitTime is surpassed. The
   * wait time can also be controlled with {@link #setMaximumWaitTime(long)} and is reset along with
   * the recorded records by {@link #reset()}.
   */
  public static void disableAwaitingIncomingRecords() {
    setMaximumWaitTime(0);
  }

  @Override
  public void open(final Controller controller) {
    this.controller = controller;
  }

  @Override
  public void export(final Record<?> record) {
    LOCK.lock();
    try {
      RECORDS.add(record.copyOf());
      IS_EMPTY.signal();
      if (controller != null) { // the engine tests do not open the exporter
        controller.updateLastExportedRecordPosition(record.getPosition());
      }
    } finally {
      LOCK.unlock();
    }
  }

  public static List<Record<?>> getRecords() {
    return RECORDS;
  }

  public static void reset() {
    LOCK.lock();
    try {
      maximumWaitTime = DEFAULT_MAX_WAIT_TIME;
      RECORDS.clear();
    } finally {
      LOCK.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static <T extends RecordValue> Stream<Record<T>> records(
      final ValueType valueType, final Class<T> valueClass) {
    final Spliterator<Record<?>> spliterator =
        Spliterators.spliteratorUnknownSize(new AwaitingRecordIterator(), Spliterator.ORDERED);
    return StreamSupport.stream(spliterator, false)
        .filter(r -> r.getValueType() == valueType)
        .map(r -> (Record<T>) r);
  }

  public static RecordStream records() {
    final Spliterator<Record<? extends RecordValue>> spliterator =
        Spliterators.spliteratorUnknownSize(new AwaitingRecordIterator(), Spliterator.ORDERED);
    return new RecordStream(
        StreamSupport.stream(spliterator, false).map(r -> (Record<RecordValue>) r));
  }

  public static MessageSubscriptionRecordStream messageSubscriptionRecords() {
    return new MessageSubscriptionRecordStream(
        records(ValueType.MESSAGE_SUBSCRIPTION, MessageSubscriptionRecordValue.class));
  }

  public static MessageSubscriptionRecordStream messageSubscriptionRecords(
      final MessageSubscriptionIntent intent) {
    return messageSubscriptionRecords().withIntent(intent);
  }

  public static MessageStartEventSubscriptionRecordStream messageStartEventSubscriptionRecords() {
    return new MessageStartEventSubscriptionRecordStream(
        records(
            ValueType.MESSAGE_START_EVENT_SUBSCRIPTION,
            MessageStartEventSubscriptionRecordValue.class));
  }

  public static MessageStartEventSubscriptionRecordStream messageStartEventSubscriptionRecords(
      final MessageStartEventSubscriptionIntent intent) {
    return messageStartEventSubscriptionRecords().withIntent(intent);
  }

  public static DeploymentRecordStream deploymentRecords() {
    return new DeploymentRecordStream(records(ValueType.DEPLOYMENT, DeploymentRecordValue.class));
  }

  public static DeploymentRecordStream deploymentRecords(final DeploymentIntent intent) {
    return deploymentRecords().withIntent(intent);
  }

  public static ProcessRecordStream processRecords() {
    return new ProcessRecordStream(records(ValueType.PROCESS, Process.class));
  }

  public static DeploymentDistributionRecordStream deploymentDistributionRecords() {
    return new DeploymentDistributionRecordStream(
        records(ValueType.DEPLOYMENT_DISTRIBUTION, DeploymentDistributionRecordValue.class));
  }

  public static JobRecordStream jobRecords() {
    return new JobRecordStream(records(ValueType.JOB, JobRecordValue.class));
  }

  public static JobRecordStream jobRecords(final JobIntent intent) {
    return jobRecords().withIntent(intent);
  }

  public static JobBatchRecordStream jobBatchRecords() {
    return new JobBatchRecordStream(records(ValueType.JOB_BATCH, JobBatchRecordValue.class));
  }

  public static JobBatchRecordStream jobBatchRecords(final JobBatchIntent intent) {
    return jobBatchRecords().withIntent(intent);
  }

  public static IncidentRecordStream incidentRecords() {
    return new IncidentRecordStream(records(ValueType.INCIDENT, IncidentRecordValue.class));
  }

  public static IncidentRecordStream incidentRecords(final IncidentIntent intent) {
    return incidentRecords().withIntent(intent);
  }

  public static ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords() {
    return new ProcessMessageSubscriptionRecordStream(
        records(
            ValueType.PROCESS_MESSAGE_SUBSCRIPTION, ProcessMessageSubscriptionRecordValue.class));
  }

  public static ProcessMessageSubscriptionRecordStream processMessageSubscriptionRecords(
      final ProcessMessageSubscriptionIntent intent) {
    return processMessageSubscriptionRecords().withIntent(intent);
  }

  public static MessageRecordStream messageRecords() {
    return new MessageRecordStream(records(ValueType.MESSAGE, MessageRecordValue.class));
  }

  public static MessageRecordStream messageRecords(final MessageIntent intent) {
    return messageRecords().withIntent(intent);
  }

  public static ProcessInstanceRecordStream processInstanceRecords() {
    return new ProcessInstanceRecordStream(
        records(ValueType.PROCESS_INSTANCE, ProcessInstanceRecordValue.class));
  }

  public static ProcessInstanceRecordStream processInstanceRecords(
      final ProcessInstanceIntent intent) {
    return processInstanceRecords().withIntent(intent);
  }

  public static ProcessInstanceBatchRecordStream processInstanceBatchRecords() {
    return new ProcessInstanceBatchRecordStream(
        records(ValueType.PROCESS_INSTANCE_BATCH, ProcessInstanceBatchRecordValue.class));
  }

  public static TimerRecordStream timerRecords() {
    return new TimerRecordStream(records(ValueType.TIMER, TimerRecordValue.class));
  }

  public static TimerRecordStream timerRecords(final TimerIntent intent) {
    return timerRecords().withIntent(intent);
  }

  public static VariableRecordStream variableRecords() {
    return new VariableRecordStream(records(ValueType.VARIABLE, VariableRecordValue.class));
  }

  public static VariableRecordStream variableRecords(final VariableIntent intent) {
    return variableRecords().withIntent(intent);
  }

  public static VariableDocumentRecordStream variableDocumentRecords() {
    return new VariableDocumentRecordStream(
        records(ValueType.VARIABLE_DOCUMENT, VariableDocumentRecordValue.class));
  }

  public static VariableDocumentRecordStream variableDocumentRecords(
      final VariableDocumentIntent intent) {
    return variableDocumentRecords().withIntent(intent);
  }

  public static ProcessInstanceCreationRecordStream processInstanceCreationRecords() {
    return new ProcessInstanceCreationRecordStream(
        records(ValueType.PROCESS_INSTANCE_CREATION, ProcessInstanceCreationRecordValue.class));
  }

  public static ProcessInstanceModificationRecordStream processInstanceModificationRecords() {
    return new ProcessInstanceModificationRecordStream(
        records(
            ValueType.PROCESS_INSTANCE_MODIFICATION, ProcessInstanceModificationRecordValue.class));
  }

  public static ProcessInstanceModificationRecordStream processInstanceModificationRecords(
      final ProcessInstanceModificationIntent intent) {
    return processInstanceModificationRecords().withIntent(intent);
  }

  public static ProcessInstanceResultRecordStream processInstanceResultRecords() {
    return new ProcessInstanceResultRecordStream(
        records(ValueType.PROCESS_INSTANCE_RESULT, ProcessInstanceResultRecordValue.class));
  }

  public static DecisionRecordStream decisionRecords() {
    return new DecisionRecordStream(records(ValueType.DECISION, DecisionRecordValue.class));
  }

  public static DecisionRequirementsRecordStream decisionRequirementsRecords() {
    return new DecisionRequirementsRecordStream(
        records(ValueType.DECISION_REQUIREMENTS, DecisionRequirementsRecordValue.class));
  }

  public static DecisionEvaluationRecordStream decisionEvaluationRecords() {
    return new DecisionEvaluationRecordStream(
        records(ValueType.DECISION_EVALUATION, DecisionEvaluationRecordValue.class));
  }

  public static class AwaitingRecordIterator implements Iterator<Record<?>> {

    private int nextIndex = 0;

    private boolean isEmpty() {
      return nextIndex >= RECORDS.size();
    }

    @Override
    public boolean hasNext() {
      LOCK.lock();
      try {
        long now = System.currentTimeMillis();
        final long endTime = now + maximumWaitTime;
        while (isEmpty() && endTime > now) {
          final long waitTime = endTime - now;
          try {
            IS_EMPTY.await(waitTime, TimeUnit.MILLISECONDS);
          } catch (final InterruptedException ignored) {
            // ignored
          }
          now = System.currentTimeMillis();
        }
        return !isEmpty();
      } finally {
        LOCK.unlock();
      }
    }

    @Override
    public Record<?> next() {
      return RECORDS.get(nextIndex++);
    }
  }
}
