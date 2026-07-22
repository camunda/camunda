/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.translate;

import io.camunda.analytics.lake.sink.RowAppender;
import io.camunda.analytics.lake.state.TranslatorState;
import io.camunda.analytics.lake.state.TranslatorState.OpenElement;
import io.camunda.analytics.lake.state.TranslatorState.OpenInstance;
import io.camunda.analytics.lake.translate.RawTableSchemas.ActivityColumns;
import io.camunda.analytics.lake.translate.RawTableSchemas.InstanceColumns;
import io.camunda.eventbridge.zeebe.connector.ZeebeRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Folds Zeebe {@code EVENT} records into finished-row appends, holding open entities in {@link
 * TranslatorState} until they complete. Only two value types matter: {@code PROCESS_INSTANCE}
 * (activation opens an instance/element row, completion/termination emits it and evicts it) and
 * {@code VARIABLE}.
 *
 * <p>Three locked rules govern correctness:
 *
 * <ul>
 *   <li><b>Root scope only</b> — an instance's variable payload is exactly the variables whose
 *       scope key is the process instance key; subprocess-/element-scoped variables are ignored by
 *       design, not omission.
 *   <li><b>Last value wins</b> — a variable put overwrites the prior value for that name, so the
 *       instance row carries the values visible at completion time.
 *   <li><b>Evict after emit</b> — an entity is deleted from state the instant its finished row is
 *       appended; state holds only <em>open</em> entities. Replaying a completion whose entity is
 *       already evicted (expected after a crash-resume before the lake's committed offset) finds no
 *       open row and is skipped silently — the same finished row is already durable in the lake.
 * </ul>
 *
 * <p>The translator never flushes or triggers the L0 sink pipelines — the app loop owns flush (and
 * thus offset-commit) policy via {@code SinkPipeline#onPollTick}.
 *
 * <h2>Backpressure</h2>
 *
 * <p>Each Zeebe record touches at most one {@link RowAppender} (an instance-completion emits to
 * {@code instances}, an element-completion emits to {@code activities}, never both). {@link
 * #onRecord(ZeebeRecord)} returns {@code false} the moment a row append's {@link
 * RowAppender#begin()} reports backpressure (ring full) — per {@code RowAppender}'s own contract,
 * the caller must then retry the very same record later rather than drop it or advance past it.
 * This is safe to do by simply calling {@link #onRecord(ZeebeRecord)} again: everything that runs
 * before the failing {@code begin()} call (state puts, {@code state.getInstance}/{@code getElement}
 * lookups) is idempotent and side-effect-free until the append actually succeeds, and eviction
 * ({@code state.deleteInstance}/{@code deleteElement}) only happens after it does.
 */
public final class LakeTranslator {

  /**
   * Variables whose JSON value exceeds this many characters are skipped (debug-logged): a guardrail
   * against a pathological blob bloating the instance row.
   */
  private static final int MAX_VARIABLE_VALUE_CHARS = 8192;

  private static final Logger LOG = LoggerFactory.getLogger(LakeTranslator.class);

  private final TranslatorState state;
  private final RowAppender instanceAppender;
  private final RowAppender activityAppender;

  public LakeTranslator(
      final TranslatorState state,
      final RowAppender instanceAppender,
      final RowAppender activityAppender) {
    this.state = state;
    this.instanceAppender = instanceAppender;
    this.activityAppender = activityAppender;
  }

  /**
   * @return {@code false} if a row append hit backpressure (ring full) — the caller must retry this
   *     same {@code zr} later instead of advancing past it (see class javadoc); {@code true}
   *     otherwise, including when the record needed no row append at all
   */
  public boolean onRecord(final ZeebeRecord zr) {
    final Record<?> record = zr.record();
    if (record.getRecordType() != RecordType.EVENT) {
      return true;
    }
    final ValueType valueType = record.getValueType();
    if (valueType == ValueType.PROCESS_INSTANCE) {
      return onProcessInstance(record);
    } else if (valueType == ValueType.VARIABLE) {
      onVariable(record);
    }
    return true;
  }

  private boolean onProcessInstance(final Record<?> record) {
    final ProcessInstanceRecordValue value = (ProcessInstanceRecordValue) record.getValue();
    final long timestamp = record.getTimestamp();
    final long processInstanceKey = value.getProcessInstanceKey();
    final long elementInstanceKey = record.getKey();
    // The process instance is the root element; its element instance key == the process instance
    // key.
    final boolean root = value.getBpmnElementType() == BpmnElementType.PROCESS;

    if (record.getIntent() == ProcessInstanceIntent.ELEMENT_ACTIVATED) {
      if (root) {
        state.putInstance(
            processInstanceKey,
            new OpenInstance(
                value.getProcessDefinitionKey(),
                value.getBpmnProcessId(),
                value.getVersion(),
                value.getTenantId(),
                timestamp));
      } else {
        final OpenInstance owner = state.getInstance(processInstanceKey);
        // Replay edge where the owner is unknown (e.g. resuming past the instance's own evict but
        // before this element's) -- the element's own timestamp is the best available family date.
        final long instanceStartMs = owner != null ? owner.startMs() : timestamp;
        state.putElement(
            elementInstanceKey,
            new OpenElement(
                processInstanceKey,
                value.getBpmnProcessId(),
                value.getVersion(),
                value.getTenantId(),
                value.getElementId(),
                value.getBpmnElementType().name(),
                timestamp,
                instanceStartMs));
      }
      return true;
    }

    final String finalState = finalStateOf(record.getIntent());
    if (finalState == null) {
      return true; // an intent other than COMPLETED/TERMINATED
    }
    if (root) {
      return emitInstance(processInstanceKey, timestamp, finalState);
    } else {
      return emitElement(elementInstanceKey, timestamp, finalState);
    }
  }

  private boolean emitElement(
      final long elementInstanceKey, final long timestamp, final String finalState) {
    final OpenElement element = state.getElement(elementInstanceKey);
    if (element == null) {
      return true; // replay past evict — expected, not an error
    }
    if (!activityAppender.begin()) {
      return false; // ring full — caller must retry this same record
    }
    activityAppender
        .putLong(ActivityColumns.INSTANCE_KEY, element.instanceKey())
        .putDict(ActivityColumns.PROCESS_ID, element.processId())
        .putInt(ActivityColumns.VERSION, element.version())
        .putDict(ActivityColumns.TENANT_ID, element.tenantId())
        .putDict(ActivityColumns.ELEMENT_ID, element.elementId())
        .putDict(ActivityColumns.ELEMENT_TYPE, element.elementType())
        .putLong(ActivityColumns.ELEMENT_KEY, elementInstanceKey)
        .putDict(ActivityColumns.STATE, finalState)
        .putLong(ActivityColumns.START_MS, element.startMs())
        .putLong(ActivityColumns.END_MS, timestamp)
        .putLong(ActivityColumns.DURATION_MS, timestamp - element.startMs())
        .putLong(ActivityColumns.INSTANCE_START_MS, element.instanceStartMs());
    activityAppender.endRow();
    state.deleteElement(elementInstanceKey);
    return true;
  }

  private boolean emitInstance(
      final long processInstanceKey, final long timestamp, final String finalState) {
    final OpenInstance instance = state.getInstance(processInstanceKey);
    if (instance == null) {
      return true; // replay past evict — expected, not an error
    }
    if (!instanceAppender.begin()) {
      return false; // ring full — caller must retry this same record
    }
    final byte[] varsJson =
        varsJson(state.variablesOf(processInstanceKey)).getBytes(StandardCharsets.UTF_8);
    instanceAppender
        .putLong(InstanceColumns.KEY, processInstanceKey)
        .putLong(InstanceColumns.PROCESS_DEFINITION_KEY, instance.processDefinitionKey())
        .putDict(InstanceColumns.PROCESS_ID, instance.processId())
        .putInt(InstanceColumns.VERSION, instance.version())
        .putDict(InstanceColumns.TENANT_ID, instance.tenantId())
        .putDict(InstanceColumns.STATE, finalState)
        .putLong(InstanceColumns.START_MS, instance.startMs())
        .putLong(InstanceColumns.END_MS, timestamp)
        .putLong(InstanceColumns.DURATION_MS, timestamp - instance.startMs())
        .putBinary(InstanceColumns.VARS_JSON, varsJson, 0, varsJson.length);
    instanceAppender.endRow();
    state.deleteInstance(processInstanceKey);
    state.deleteVariablesOf(processInstanceKey);
    return true;
  }

  private void onVariable(final Record<?> record) {
    if (record.getIntent() != VariableIntent.CREATED
        && record.getIntent() != VariableIntent.UPDATED) {
      return;
    }
    final VariableRecordValue value = (VariableRecordValue) record.getValue();
    // Root scope only: a variable belongs to the instance row only when its scope is the process
    // instance itself. Non-root (subprocess-/element-scoped) variables are ignored by design.
    if (value.getScopeKey() != value.getProcessInstanceKey()) {
      return;
    }
    final String valueJson = value.getValue();
    if (valueJson.length() > MAX_VARIABLE_VALUE_CHARS) {
      LOG.debug(
          "Skipping oversized variable '{}' ({} chars) on instance {}",
          value.getName(),
          valueJson.length(),
          value.getProcessInstanceKey());
      return;
    }
    state.putVariable(value.getProcessInstanceKey(), value.getName(), valueJson);
  }

  private static String finalStateOf(final Intent intent) {
    if (intent == ProcessInstanceIntent.ELEMENT_COMPLETED) {
      return "COMPLETED";
    }
    if (intent == ProcessInstanceIntent.ELEMENT_TERMINATED) {
      return "TERMINATED";
    }
    return null;
  }

  /**
   * Builds the instance variable payload: a JSON object whose keys are JSON-escaped variable names
   * and whose values are inserted <b>raw</b> (variable values are already JSON documents). {@code
   * "{}"} when there are no variables.
   */
  private static String varsJson(final Map<String, String> variables) {
    if (variables.isEmpty()) {
      return "{}";
    }
    final StringBuilder json = new StringBuilder(2 + variables.size() * 16);
    json.append('{');
    boolean first = true;
    for (final Map.Entry<String, String> entry : variables.entrySet()) {
      if (!first) {
        json.append(',');
      }
      first = false;
      json.append('"');
      escapeJson(json, entry.getKey());
      json.append("\":").append(entry.getValue());
    }
    return json.append('}').toString();
  }

  /** Appends {@code raw} to {@code out} escaped as the contents of a JSON string. */
  private static void escapeJson(final StringBuilder out, final String raw) {
    for (int i = 0; i < raw.length(); i++) {
      final char c = raw.charAt(i);
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\b' -> out.append("\\b");
        case '\f' -> out.append("\\f");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < 0x20) {
            out.append(String.format("\\u%04x", (int) c));
          } else {
            out.append(c);
          }
        }
      }
    }
  }
}
