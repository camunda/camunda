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
package io.zeebe.test.util.record;

import io.zeebe.exporter.api.record.Record;
import io.zeebe.exporter.api.record.RecordValue;
import io.zeebe.exporter.api.record.value.DeploymentRecordValue;
import io.zeebe.exporter.api.record.value.IncidentRecordValue;
import io.zeebe.exporter.api.record.value.JobBatchRecordValue;
import io.zeebe.exporter.api.record.value.JobRecordValue;
import io.zeebe.exporter.api.record.value.MessageRecordValue;
import io.zeebe.exporter.api.record.value.MessageStartEventSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.MessageSubscriptionRecordValue;
import io.zeebe.exporter.api.record.value.TimerRecordValue;
import io.zeebe.exporter.api.record.value.VariableDocumentRecordValue;
import io.zeebe.exporter.api.record.value.VariableRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceCreationRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.api.record.value.WorkflowInstanceSubscriptionRecordValue;
import io.zeebe.exporter.api.spi.Exporter;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.DeploymentIntent;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.JobBatchIntent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.MessageIntent;
import io.zeebe.protocol.intent.MessageStartEventSubscriptionIntent;
import io.zeebe.protocol.intent.MessageSubscriptionIntent;
import io.zeebe.protocol.intent.TimerIntent;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.VariableIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.intent.WorkflowInstanceSubscriptionIntent;
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

public class RecordingExporter implements Exporter {

  private static final List<Record<?>> RECORDS = new CopyOnWriteArrayList<>();

  private static final Lock LOCK = new ReentrantLock();
  private static final Condition IS_EMPTY = LOCK.newCondition();

  private static final long MAX_WAIT = Duration.ofSeconds(5).toMillis();

  @Override
  public void export(final Record record) {
    LOCK.lock();
    try {
      RECORDS.add(record);
      IS_EMPTY.signal();
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
      RECORDS.clear();
    } finally {
      LOCK.unlock();
    }
  }

  @SuppressWarnings("unchecked")
  protected static <T extends RecordValue> Stream<Record<T>> records(
      final ValueType valueType, final Class<T> valueClass) {
    final Spliterator<Record<?>> spliterator =
        Spliterators.spliteratorUnknownSize(new RecordIterator(), Spliterator.ORDERED);
    return StreamSupport.stream(spliterator, false)
        .filter(r -> r.getMetadata().getValueType() == valueType)
        .map(r -> (Record<T>) r);
  }

  public static RecordStream records() {
    final Spliterator<Record<? extends RecordValue>> spliterator =
        Spliterators.spliteratorUnknownSize(new RecordIterator(), Spliterator.ORDERED);
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

  public static WorkflowInstanceSubscriptionRecordStream workflowInstanceSubscriptionRecords() {
    return new WorkflowInstanceSubscriptionRecordStream(
        records(
            ValueType.WORKFLOW_INSTANCE_SUBSCRIPTION,
            WorkflowInstanceSubscriptionRecordValue.class));
  }

  public static WorkflowInstanceSubscriptionRecordStream workflowInstanceSubscriptionRecords(
      final WorkflowInstanceSubscriptionIntent intent) {
    return workflowInstanceSubscriptionRecords().withIntent(intent);
  }

  public static MessageRecordStream messageRecords() {
    return new MessageRecordStream(records(ValueType.MESSAGE, MessageRecordValue.class));
  }

  public static MessageRecordStream messageRecords(final MessageIntent intent) {
    return messageRecords().withIntent(intent);
  }

  public static WorkflowInstanceRecordStream workflowInstanceRecords() {
    return new WorkflowInstanceRecordStream(
        records(ValueType.WORKFLOW_INSTANCE, WorkflowInstanceRecordValue.class));
  }

  public static WorkflowInstanceRecordStream workflowInstanceRecords(
      final WorkflowInstanceIntent intent) {
    return workflowInstanceRecords().withIntent(intent);
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

  public static WorkflowInstanceCreationRecordStream workflowInstanceCreationRecords() {
    return new WorkflowInstanceCreationRecordStream(
        records(ValueType.WORKFLOW_INSTANCE_CREATION, WorkflowInstanceCreationRecordValue.class));
  }

  public static class RecordIterator implements Iterator<Record<?>> {

    private int nextIndex = 0;

    private boolean isEmpty() {
      return nextIndex >= RECORDS.size();
    }

    @Override
    public boolean hasNext() {
      LOCK.lock();
      try {
        long now = System.currentTimeMillis();
        final long endTime = now + MAX_WAIT;
        while (isEmpty() && endTime > now) {
          final long waitTime = endTime - now;
          try {
            IS_EMPTY.await(waitTime, TimeUnit.MILLISECONDS);
          } catch (final InterruptedException e) {
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
