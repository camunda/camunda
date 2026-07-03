/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.eventbridge.analytics.element.ElementExecutionFact;
import io.camunda.eventbridge.analytics.fact.IncidentCohortFact;
import io.camunda.eventbridge.analytics.fact.IncidentDurationFact;
import io.camunda.eventbridge.analytics.fact.IncidentFact;
import io.camunda.eventbridge.analytics.fact.ProcessDefinitionFact;
import io.camunda.eventbridge.analytics.fact.ProcessExecutionFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceExecutionTimeFact;
import io.camunda.eventbridge.analytics.fact.ProcessInstanceLifecycleFact;
import io.camunda.eventbridge.analytics.fact.SlaCohortFact;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.eventbridge.streaming.fold.Projector;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.IncidentIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.IncidentRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.Process;
import java.nio.charset.StandardCharsets;

/**
 * The single base-projection fold: each consumed record is processed once. Every element — the root
 * {@code PROCESS} included, since a process instance is just the root element — is tracked the same
 * way: {@code ELEMENT_ACTIVATED} records its start time, and {@code ELEMENT_COMPLETED}/{@code
 * ELEMENT_TERMINATED} reads that start, derives the duration, and emits a fact:
 *
 * <ul>
 *   <li>the {@code PROCESS} element completing → a {@link ProcessInstanceExecutionTimeFact},
 *       enriched with the instance's variables (so it can be grouped by e.g. region);
 *   <li>any other element completing → an {@link ElementExecutionFact};
 *   <li>{@code VARIABLE} records accumulate the instance's variables.
 * </ul>
 *
 * <p>The start is deleted on completion, so a duplicate completion derives nothing — that is the
 * emit-once guard (no separate flag needed). The runtime fans the emitted {@link
 * ProcessExecutionFact}s out to the rollup(s) for each concrete type, so multiple metrics are
 * served from this one projection without re-reading the stream. The fold is a deterministic
 * function of the in-order, per-partition source stream.
 */
public final class ProcessExecutionProjector
    implements Projector<ZeebeRecord, ProcessExecutionFact> {

  private final BaseProjectionStore store;
  private final KeyValueStore<DbString, DbLong> elementStarts;
  private final DbString elementKey = new DbString();
  private final DbLong startTime = new DbLong();

  public ProcessExecutionProjector(final BaseProjectionStore store) {
    this(store, new InMemoryKeyValueStore<>(new DbString(), new DbLong()));
  }

  public ProcessExecutionProjector(
      final BaseProjectionStore store, final KeyValueStore<DbString, DbLong> elementStarts) {
    this.store = store;
    this.elementStarts = elementStarts;
  }

  @Override
  public void checkpoint() {
    // Flush the write-back-cached base projection (variables, element starts, incidents) to durable
    // storage. Runs inside the runtime's checkpoint transaction, alongside the rollups and offset.
    store.checkpoint();
  }

  @Override
  public void apply(final ZeebeRecord zeebeRecord, final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();

    if (record.getValueType() == ValueType.VARIABLE
        && record.getValue() instanceof final VariableRecordValue variable) {
      store.putVariable(
          variable.getProcessInstanceKey(), variable.getName(), unquote(variable.getValue()));
      return;
    }

    // A deployed process definition: emit its BPMN so the dashboard can render the model behind the
    // flow-node heatmap. Emitted on CREATED (one per newly deployed version).
    if (record.getValueType() == ValueType.PROCESS
        && record.getIntent() == ProcessIntent.CREATED
        && record.getValue() instanceof final Process process) {
      out.collect(
          new ProcessDefinitionFact(
              process.getBpmnProcessId(),
              process.getProcessDefinitionKey(),
              process.getVersion(),
              process.getTenantId(),
              new String(process.getResource(), StandardCharsets.UTF_8)));
      return;
    }

    // Incidents: +1 on create, -1 on resolve (per flow node) for the incident-frequency/open
    // metrics, and a per-instance flag so the completion fact can record whether the instance ever
    // had an incident (multiple incidents on one instance set the flag once).
    if (record.getValueType() == ValueType.INCIDENT
        && record.getValue() instanceof final IncidentRecordValue incident
        && record.getIntent() instanceof final IncidentIntent incidentIntent) {
      if (incidentIntent == IncidentIntent.CREATED) {
        final boolean first = store.markIncident(incident.getProcessInstanceKey());
        store.putIncidentStart(incident.getElementInstanceKey(), record.getTimestamp());
        out.collect(incidentFact(zeebeRecord, incident, 1L));
        if (first) {
          // the instance's first incident: reclassify its start cohort as "had an incident". Stamp
          // it with the instance's START time (the process element's activation) so it lands in the
          // same cohort as the started signal — a forward-looking, distinct-per-instance count.
          elementKey.wrapString(
              incident.getProcessInstanceKey() + ":" + incident.getBpmnProcessId());
          elementStarts
              .get(elementKey)
              .ifPresent(
                  start ->
                      out.collect(
                          new IncidentCohortFact(
                              incident.getBpmnProcessId(),
                              incident.getProcessDefinitionKey(),
                              incident.getTenantId(),
                              start.getValue(),
                              false,
                              zeebeRecord.partitionId(),
                              zeebeRecord.offset())));
        }
      } else if (incidentIntent == IncidentIntent.RESOLVED) {
        out.collect(incidentFact(zeebeRecord, incident, -1L));
        // pair with the earlier CREATE (by element-instance key) to derive the open→resolve time
        final long createdAt = store.takeIncidentStart(incident.getElementInstanceKey());
        if (createdAt != BaseProjectionStore.NO_POSITION) {
          out.collect(
              new IncidentDurationFact(
                  incident.getBpmnProcessId(),
                  incident.getElementId(),
                  incident.getTenantId(),
                  record.getTimestamp() - createdAt,
                  record.getTimestamp(),
                  zeebeRecord.partitionId(),
                  zeebeRecord.offset()));
        }
      }
      return;
    }

    if (record.getValueType() != ValueType.PROCESS_INSTANCE
        || !(record.getValue() instanceof final ProcessInstanceRecordValue value)
        || !(record.getIntent() instanceof final ProcessInstanceIntent intent)) {
      return;
    }

    foldElement(zeebeRecord, value, intent, out);
  }

  /**
   * Track an element's activation time; on completion derive its fact (process or plain element).
   */
  private void foldElement(
      final ZeebeRecord zeebeRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    // record key is not carried in the consumed stream, so key on (instance, element)
    elementKey.wrapString(value.getProcessInstanceKey() + ":" + value.getElementId());
    switch (intent) {
      case ELEMENT_ACTIVATED -> {
        startTime.wrapLong(record.getTimestamp());
        elementStarts.put(elementKey, startTime);
        if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
          out.collect(lifecycle(zeebeRecord, value, 1L)); // an instance became active
          // the "started" signal for this instance's SLA start cohort (event time = start time)
          out.collect(
              new SlaCohortFact(
                  value.getBpmnProcessId(),
                  value.getProcessDefinitionKey(),
                  value.getVersion(),
                  value.getTenantId(),
                  record.getTimestamp(),
                  true,
                  false,
                  0L,
                  zeebeRecord.partitionId(),
                  zeebeRecord.offset()));
          // the "started" signal for the forward-looking no-incident cohort (same start window)
          out.collect(
              new IncidentCohortFact(
                  value.getBpmnProcessId(),
                  value.getProcessDefinitionKey(),
                  value.getTenantId(),
                  record.getTimestamp(),
                  true,
                  zeebeRecord.partitionId(),
                  zeebeRecord.offset()));
        }
      }
      case ELEMENT_COMPLETED, ELEMENT_TERMINATED ->
          elementStarts
              .get(elementKey)
              .ifPresent(
                  start -> {
                    emit(zeebeRecord, value, intent, record.getTimestamp() - start.getValue(), out);
                    elementStarts.delete(elementKey);
                  });
      default -> {
        // other element lifecycle intents do not bound execution time
      }
    }
  }

  private void emit(
      final ZeebeRecord zeebeRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final long duration,
      final Collector<ProcessExecutionFact> out) {
    final Record<?> record = zeebeRecord.record();
    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      final boolean hadIncident = store.hasIncident(value.getProcessInstanceKey());
      out.collect(
          new ProcessInstanceExecutionTimeFact(
              value.getProcessInstanceKey(),
              value.getProcessDefinitionKey(),
              value.getBpmnProcessId(),
              value.getVersion(),
              value.getTenantId(),
              record.getTimestamp() - duration,
              record.getTimestamp(),
              duration,
              intent == ProcessInstanceIntent.ELEMENT_COMPLETED,
              hadIncident,
              zeebeRecord.partitionId(),
              zeebeRecord.offset(),
              store.getVariables(value.getProcessInstanceKey())));
      out.collect(lifecycle(zeebeRecord, value, -1L)); // an instance is no longer active
      // the "outcome" signal for the SLA start cohort — event time is the instance's START time
      // (endTime - duration) so it lands in the same cohort as its "started" signal.
      out.collect(
          new SlaCohortFact(
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              value.getVersion(),
              value.getTenantId(),
              record.getTimestamp() - duration,
              false,
              intent == ProcessInstanceIntent.ELEMENT_COMPLETED,
              duration,
              zeebeRecord.partitionId(),
              zeebeRecord.offset()));
      store.deleteVariables(value.getProcessInstanceKey());
      store.clearIncident(value.getProcessInstanceKey());
    } else {
      out.collect(
          new ElementExecutionFact(
              value.getBpmnProcessId(),
              value.getProcessDefinitionKey(),
              value.getVersion(),
              value.getTenantId(),
              value.getElementId(),
              value.getBpmnElementType().name(),
              duration,
              record.getTimestamp(),
              zeebeRecord.partitionId(),
              zeebeRecord.offset()));
    }
  }

  /** A signed in-flight delta for the process instance ({@code +1} activate, {@code -1} end). */
  private static ProcessInstanceLifecycleFact lifecycle(
      final ZeebeRecord zeebeRecord, final ProcessInstanceRecordValue value, final long delta) {
    return new ProcessInstanceLifecycleFact(
        value.getBpmnProcessId(),
        value.getProcessDefinitionKey(),
        value.getVersion(),
        value.getTenantId(),
        delta,
        zeebeRecord.record().getTimestamp(),
        zeebeRecord.partitionId(),
        zeebeRecord.offset());
  }

  /** A signed incident-count change for the flow node that raised/resolved it. */
  private static IncidentFact incidentFact(
      final ZeebeRecord zeebeRecord, final IncidentRecordValue incident, final long delta) {
    return new IncidentFact(
        incident.getBpmnProcessId(),
        incident.getElementId(),
        incident.getTenantId(),
        incident.getErrorType() == null ? "UNKNOWN" : incident.getErrorType().name(),
        delta,
        zeebeRecord.record().getTimestamp(),
        zeebeRecord.partitionId(),
        zeebeRecord.offset());
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
