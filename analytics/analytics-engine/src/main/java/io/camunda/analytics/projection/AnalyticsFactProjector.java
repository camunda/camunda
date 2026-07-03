/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.projection;

import io.camunda.analytics.dataset.EnrichmentTiming;
import io.camunda.analytics.fact.Fact;
import io.camunda.analytics.fact.FactType;
import io.camunda.analytics.fact.Transition;
import io.camunda.eventbridge.streaming.fold.Collector;
import io.camunda.eventbridge.streaming.fold.Projector;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.memory.InMemoryKeyValueStore;
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
import java.util.Map;

/**
 * The generic base-projection fold: each consumed record is processed once and emits {@link Fact}s
 * — the uniform, {@link FactType}-tagged, transition-tagged replacement for the per-metric fact
 * records. Every element (the root {@code PROCESS} included, since an instance is just the root
 * element) is tracked the same way: {@code ELEMENT_ACTIVATED} records its start and emits an {@code
 * ACTIVATED} fact; {@code ELEMENT_COMPLETED}/{@code TERMINATED} reads the start, derives the
 * duration, and emits a {@code COMPLETED}/{@code TERMINATED} fact. Incidents emit {@code CREATED}/
 * {@code RESOLVED} facts (with a resolution duration when paired), and a deployed process emits a
 * {@code DEPLOYED} definition fact.
 *
 * <p>This is the canonical, small fact set; the specialized cohort metrics (SLA, no-incident) are
 * expressed as dataset declarations over these facts rather than as bespoke fold outputs. The fold
 * is a deterministic function of the in-order, per-partition source stream and reuses the shared
 * {@link BaseProjectionStore} (variables, incident flags/starts). It is the sole projector — the
 * generic replacement for the retired per-metric typed projectors.
 *
 * <p>Variable dimensions are stamped namespaced as {@code var.<name>} so they never collide with a
 * structural field. Enrichment uses the at-completion snapshot ({@link EnrichmentTiming#EVENT_TIME}
 * for a completion event); {@link EnrichmentTiming#PI_CREATE}/{@link EnrichmentTiming#PI_COMPLETE}
 * are wired via {@link VariableEnricher} once a dataset declares them.
 */
public final class AnalyticsFactProjector implements Projector<SourceRecord, Fact> {

  /** Namespace prefix for a variable dimension field, e.g. {@code var.region}. */
  public static final String VAR_PREFIX = "var.";

  private final BaseProjectionStore store;
  private final KeyValueStore<DbString, DbLong> elementStarts;
  private final DbString elementKey = new DbString();
  private final DbLong startTime = new DbLong();

  public AnalyticsFactProjector(final BaseProjectionStore store) {
    this(store, new InMemoryKeyValueStore<>(new DbString(), new DbLong()));
  }

  public AnalyticsFactProjector(
      final BaseProjectionStore store, final KeyValueStore<DbString, DbLong> elementStarts) {
    this.store = store;
    this.elementStarts = elementStarts;
  }

  @Override
  public void checkpoint() {
    store.checkpoint();
  }

  @Override
  public boolean needsCheckpoint() {
    return store.needsCheckpoint();
  }

  @Override
  public void apply(final SourceRecord sourceRecord, final Collector<Fact> out) {
    final Record<?> record = sourceRecord.record();

    if (record.getValueType() == ValueType.VARIABLE
        && record.getValue() instanceof final VariableRecordValue variable) {
      store.putVariable(
          variable.getProcessInstanceKey(), variable.getName(), unquote(variable.getValue()));
      return;
    }

    if (record.getValueType() == ValueType.PROCESS
        && record.getIntent() == ProcessIntent.CREATED
        && record.getValue() instanceof final Process process) {
      out.collect(
          Fact.builder(FactType.PROCESS_DEFINITION)
              .eventTime(record.getTimestamp())
              .source(sourceRecord.partitionId(), sourceRecord.offset())
              .transition(Transition.DEPLOYED)
              .field("bpmnProcessId", process.getBpmnProcessId())
              .field("processDefinitionKey", process.getProcessDefinitionKey())
              .field("version", process.getVersion())
              .field("tenantId", process.getTenantId())
              .field("bpmnXml", new String(process.getResource(), StandardCharsets.UTF_8))
              .build());
      return;
    }

    if (record.getValueType() == ValueType.INCIDENT
        && record.getValue() instanceof final IncidentRecordValue incident
        && record.getIntent() instanceof final IncidentIntent incidentIntent) {
      foldIncident(sourceRecord, incident, incidentIntent, out);
      return;
    }

    if (record.getValueType() == ValueType.PROCESS_INSTANCE
        && record.getValue() instanceof final ProcessInstanceRecordValue value
        && record.getIntent() instanceof final ProcessInstanceIntent intent) {
      foldElement(sourceRecord, value, intent, out);
    }
  }

  private void foldIncident(
      final SourceRecord sourceRecord,
      final IncidentRecordValue incident,
      final IncidentIntent intent,
      final Collector<Fact> out) {
    final Record<?> record = sourceRecord.record();
    final String errorType =
        incident.getErrorType() == null ? "UNKNOWN" : incident.getErrorType().name();
    if (intent == IncidentIntent.CREATED) {
      store.markIncident(incident.getProcessInstanceKey());
      store.putIncidentStart(incident.getElementInstanceKey(), record.getTimestamp());
      out.collect(incidentFact(sourceRecord, incident, Transition.CREATED, errorType, 1L).build());
    } else if (intent == IncidentIntent.RESOLVED) {
      final Fact.Builder resolved =
          incidentFact(sourceRecord, incident, Transition.RESOLVED, errorType, -1L);
      final long createdAt = store.takeIncidentStart(incident.getElementInstanceKey());
      if (createdAt != BaseProjectionStore.NO_POSITION) {
        resolved
            .field("durationMs", record.getTimestamp() - createdAt)
            .field("resolvedTimeMs", record.getTimestamp());
      }
      out.collect(resolved.build());
    }
  }

  private Fact.Builder incidentFact(
      final SourceRecord sourceRecord,
      final IncidentRecordValue incident,
      final Transition transition,
      final String errorType,
      final long delta) {
    return Fact.builder(FactType.INCIDENT)
        .eventTime(sourceRecord.record().getTimestamp())
        .source(sourceRecord.partitionId(), sourceRecord.offset())
        .transition(transition)
        .field("bpmnProcessId", incident.getBpmnProcessId())
        .field("elementId", incident.getElementId())
        .field("tenantId", incident.getTenantId())
        .field("errorType", errorType)
        .field("delta", delta);
  }

  private void foldElement(
      final SourceRecord sourceRecord,
      final ProcessInstanceRecordValue value,
      final ProcessInstanceIntent intent,
      final Collector<Fact> out) {
    final Record<?> record = sourceRecord.record();
    final boolean isProcess = value.getBpmnElementType() == BpmnElementType.PROCESS;
    elementKey.wrapString(value.getProcessInstanceKey() + ":" + value.getElementId());
    switch (intent) {
      case ELEMENT_ACTIVATED -> {
        startTime.wrapLong(record.getTimestamp());
        elementStarts.put(elementKey, startTime);
        out.collect(base(sourceRecord, value, isProcess, Transition.ACTIVATED).build());
      }
      case ELEMENT_COMPLETED, ELEMENT_TERMINATED ->
          elementStarts
              .get(elementKey)
              .ifPresent(
                  start -> {
                    emitCompletion(
                        sourceRecord,
                        value,
                        isProcess,
                        intent == ProcessInstanceIntent.ELEMENT_COMPLETED
                            ? Transition.COMPLETED
                            : Transition.TERMINATED,
                        record.getTimestamp() - start.getValue(),
                        out);
                    elementStarts.delete(elementKey);
                  });
      default -> {
        // other element lifecycle intents do not bound execution time
      }
    }
  }

  private void emitCompletion(
      final SourceRecord sourceRecord,
      final ProcessInstanceRecordValue value,
      final boolean isProcess,
      final Transition transition,
      final long durationMs,
      final Collector<Fact> out) {
    final Record<?> record = sourceRecord.record();
    final Fact.Builder fact =
        base(sourceRecord, value, isProcess, transition)
            .field("startTime", record.getTimestamp() - durationMs)
            .field("endTime", record.getTimestamp())
            .field("durationMs", durationMs);
    if (isProcess) {
      fact.field("processInstanceKey", value.getProcessInstanceKey())
          .field("completedNormally", transition == Transition.COMPLETED)
          .field("hadIncident", store.hasIncident(value.getProcessInstanceKey()));
      // EVENT_TIME enrichment: the variable snapshot as of this completion event.
      final Map<String, String> variables = store.getVariables(value.getProcessInstanceKey());
      variables.forEach((name, val) -> fact.field(VAR_PREFIX + name, val));
      out.collect(fact.build());
      store.deleteVariables(value.getProcessInstanceKey());
      store.clearIncident(value.getProcessInstanceKey());
    } else {
      fact.field("elementId", value.getElementId())
          .field("elementType", value.getBpmnElementType().name());
      out.collect(fact.build());
    }
  }

  /** The common structural fields + transition for a process-instance or element fact. */
  private Fact.Builder base(
      final SourceRecord sourceRecord,
      final ProcessInstanceRecordValue value,
      final boolean isProcess,
      final Transition transition) {
    return Fact.builder(isProcess ? FactType.PROCESS_INSTANCE : FactType.ELEMENT)
        .eventTime(sourceRecord.record().getTimestamp())
        .source(sourceRecord.partitionId(), sourceRecord.offset())
        .transition(transition)
        .field("bpmnProcessId", value.getBpmnProcessId())
        .field("processDefinitionKey", value.getProcessDefinitionKey())
        .field("version", value.getVersion())
        .field("tenantId", value.getTenantId());
  }

  /** Variable values arrive as JSON; strip the quotes from a JSON string so {@code "EU"} → EU. */
  private static String unquote(final String jsonValue) {
    if (jsonValue.length() >= 2 && jsonValue.startsWith("\"") && jsonValue.endsWith("\"")) {
      return jsonValue.substring(1, jsonValue.length() - 1);
    }
    return jsonValue;
  }
}
