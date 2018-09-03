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
package io.zeebe.test.util;

import io.zeebe.exporter.record.Record;
import io.zeebe.exporter.record.RecordValue;
import io.zeebe.exporter.record.value.IncidentRecordValue;
import io.zeebe.exporter.record.value.JobRecordValue;
import io.zeebe.exporter.record.value.WorkflowInstanceRecordValue;
import io.zeebe.exporter.spi.Exporter;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.intent.IncidentIntent;
import io.zeebe.protocol.intent.Intent;
import io.zeebe.protocol.intent.JobIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RecordingExporter implements Exporter {

  public static final List<Record> RECORDS = new CopyOnWriteArrayList<>();

  public static void reset() {
    RECORDS.clear();
  }

  @Override
  public void export(Record record) {
    RECORDS.add(record);
  }

  // Job records

  public static Stream<JobRecordValue> jobEvents(final JobIntent intent) {
    return eventsOfType(ValueType.JOB, intent).map(asJobValue());
  }

  public static Stream<JobRecordValue> jobCommands(final JobIntent intent) {
    return commandsOfType(ValueType.JOB, intent).map(asJobValue());
  }

  public static boolean hasJobEvent(final JobIntent intent) {
    return hasRecord(jobEvents(intent));
  }

  public static boolean hasJobEvent(
      final JobIntent intent, final Predicate<JobRecordValue> predicate) {
    return hasRecord(jobEvents(intent).filter(predicate));
  }

  public static boolean hasJobCommand(final JobIntent intent) {
    return hasRecord(jobCommands(intent));
  }

  public static List<JobRecordValue> getJobEvents(final JobIntent intent) {
    return asList(jobEvents(intent));
  }

  public static JobRecordValue getFirstJobEvent(final JobIntent intent) {
    return getFirst(jobEvents(intent));
  }

  public static JobRecordValue getFirstJobCommand(final JobIntent intent) {
    return getFirst(jobCommands(intent));
  }

  // Incident records

  public static Stream<Record> incidentRecords(final IncidentIntent intent) {
    return recordsOfType(ValueType.INCIDENT).filter(withIntent(intent));
  }

  public static Stream<IncidentRecordValue> incidentEvents(final IncidentIntent intent) {
    return eventsOfType(ValueType.INCIDENT, intent).map(asIncidentValue());
  }

  public static boolean hasIncidentEvent(final IncidentIntent intent) {
    return hasRecord(incidentEvents(intent));
  }

  public static List<Record> getIncidentRecords(final IncidentIntent intent) {
    return asList(incidentRecords(intent));
  }

  // WorkflowInstance records

  public static Stream<WorkflowInstanceRecordValue> workflowInstanceEvents(
      final WorkflowInstanceIntent intent) {
    return eventsOfType(ValueType.WORKFLOW_INSTANCE, intent).map(asWorkflowInstanceValue());
  }

  public static boolean hasWorkflowInstanceEvent(final WorkflowInstanceIntent intent) {
    return hasRecord(workflowInstanceEvents(intent));
  }

  public static boolean hasWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent, Predicate<WorkflowInstanceRecordValue> predicate) {
    return hasRecord(workflowInstanceEvents(intent).filter(predicate));
  }

  public static WorkflowInstanceRecordValue getFirstWorkflowInstanceEvent(
      final WorkflowInstanceIntent intent) {
    return getFirst(workflowInstanceEvents(intent));
  }

  // Activity records

  // Delete after https://github.com/zeebe-io/zeebe/issues/1242 is implemented and use instead
  // getFirstActivityEvent
  public static Record getFirstActivityRecord(
      final String activityId, final WorkflowInstanceIntent intent) {
    return getFirst(
        eventsOfType(ValueType.WORKFLOW_INSTANCE, intent)
            .filter(r -> activityId.equals(asWorkflowInstanceValue().apply(r).getActivityId())));
  }

  public static boolean hasActivityEvent(
      final String activityId, final WorkflowInstanceIntent intent) {
    return hasRecord(workflowInstanceEvents(intent).filter(withActivityId(activityId)));
  }

  public static WorkflowInstanceRecordValue getFirstActivityEvent(
      final String activityId, final WorkflowInstanceIntent intent) {
    return getFirst(workflowInstanceEvents(intent).filter(withActivityId(activityId)));
  }

  public static List<WorkflowInstanceRecordValue> getActivityEvents(
      final String activityId, final WorkflowInstanceIntent intent) {
    return asList(workflowInstanceEvents(intent).filter(withActivityId(activityId)));
  }

  // Record streams

  public static Stream<Record> recordsOfType(final ValueType valueType) {
    return RECORDS.stream().filter(withValueType(valueType));
  }

  public static Stream<Record> recordsOfType(
      final ValueType valueType, final RecordType recordType) {
    return recordsOfType(valueType).filter(withRecordType(recordType));
  }

  public static Stream<Record> eventsOfType(final ValueType valueType) {
    return recordsOfType(valueType, RecordType.EVENT);
  }

  public static Stream<Record> eventsOfType(final ValueType valueType, final Intent intent) {
    return eventsOfType(valueType).filter(withIntent(intent));
  }

  public static Stream<Record> commandsOfType(final ValueType valueType) {
    return recordsOfType(valueType, RecordType.COMMAND);
  }

  public static Stream<Record> commandsOfType(final ValueType valueType, final Intent intent) {
    return commandsOfType(valueType).filter(withIntent(intent));
  }

  // Metadata predicates

  public static Predicate<Record> withValueType(final ValueType valueType) {
    return r -> r.getMetadata().getValueType() == valueType;
  }

  public static Predicate<Record> withRecordType(final RecordType recordType) {
    return r -> r.getMetadata().getRecordType() == recordType;
  }

  public static Predicate<Record> withIntent(final Intent intent) {
    return r -> r.getMetadata().getIntent() == intent;
  }

  public static Predicate<Record> withKey(final long key) {
    return r -> r.getKey() == key;
  }

  // Workflow instance record predicates

  public static Predicate<WorkflowInstanceRecordValue> withActivityId(final String activityId) {
    return v -> activityId.equals(v.getActivityId());
  }

  public static Predicate<WorkflowInstanceRecordValue> withWorkflowInstanceKey(
      final long workflowInstanceKey) {
    return v -> v.getWorkflowInstanceKey() == workflowInstanceKey;
  }

  // Job record predicates

  public static Predicate<JobRecordValue> withRetries(final int retries) {
    return v -> v.getRetries() == retries;
  }

  // Mapping to record values

  @SuppressWarnings("unchecked")
  public static <V extends RecordValue> Function<Record, V> asRecordValue(Class<V> valueClass) {
    return r -> (V) r.getValue();
  }

  public static Function<Record, JobRecordValue> asJobValue() {
    return asRecordValue(JobRecordValue.class);
  }

  public static Function<Record, IncidentRecordValue> asIncidentValue() {
    return asRecordValue(IncidentRecordValue.class);
  }

  public static Function<Record, WorkflowInstanceRecordValue> asWorkflowInstanceValue() {
    return asRecordValue(WorkflowInstanceRecordValue.class);
  }

  // Stream helpers
  public static boolean hasRecord(Stream<?> stream) {
    return stream.findAny().isPresent();
  }

  public static <R> List<R> asList(Stream<R> stream) {
    return stream.collect(Collectors.toList());
  }

  public static <R> R getFirst(Stream<R> stream) {
    return stream
        .findFirst()
        .orElseThrow(() -> new AssertionError("No record found matching criteria"));
  }
}
