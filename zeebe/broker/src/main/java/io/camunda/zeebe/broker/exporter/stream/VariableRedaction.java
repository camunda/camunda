/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Proof of concept for permanently redacting sensitive process variables (product-hub #3805,
 * PoC-2).
 *
 * <p>Applied once per record in {@link RecordExporter#wrap}, which decodes the logged event into
 * the single record value instance that is then handed to every exporter in turn. Redacting there
 * means the Camunda exporter, the RDBMS exporter, the raw-record Elasticsearch/OpenSearch exporters
 * that feed Optimize, and any customer-built exporter all receive an already-redacted record, with
 * no per-exporter duplication.
 *
 * <p>Only the decoded copy is rewritten. The Raft log, RocksDB and snapshots keep the plain value,
 * so the engine still evaluates FEEL against it and job workers still receive it.
 *
 * <p>Unlike PoC-1, this class does not decide which protection applies to a variable -- it only
 * enforces a decision made upstream. The name-pattern match happens once, in {@link
 * io.camunda.zeebe.engine.processing.variable.VariableBehavior}, when the variable is set, and the
 * verdict is carried on the record as {@link
 * io.camunda.zeebe.protocol.record.value.VariableRecordValue#getProtectionModes()}. This is what
 * lets every downstream consumer -- this class, but also a future masking/encryption check -- read
 * a declared set instead of re-parsing the name. {@code MASK}/{@code ENCRYPT} are declarable today
 * but not yet enforced anywhere -- a known gap until masking/encryption behavior is built.
 *
 * <p>A variable that is protected only because a key nested in its value matches a pattern keeps
 * its structure: just those keys become {@code null}, e.g. {@code {"sensitive_ssn": null}}. A
 * variable whose own name matches is redacted as a whole.
 *
 * <p>{@link ValueType#DECISION_EVALUATION} records are covered the same way: the engine flags each
 * evaluated input whose expression references a sensitive variable, and its input value is redacted
 * here.
 *
 * <p>Incident error messages are not handled here: free text cannot be redacted without the
 * variable values, so the engine masks them when it creates the incident.
 *
 * <p>PoC limitations, deliberate: job payloads, process instance creation payloads, decision
 * outputs and message correlation keys carry variable values on other record types or fields and
 * are untouched.
 */
final class VariableRedaction {

  /**
   * The value every redacted value is replaced with: JSON {@code null}. The record keeps its
   * protection modes, so consumers tell a redacted value apart from a genuine {@code null} by
   * reading them rather than by recognizing a marker string.
   */
  private static final DirectBuffer REDACTED_VALUE =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack("null"));

  private VariableRedaction() {}

  private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

  /**
   * Replaces every value in {@code recordValue} declared with {@link
   * io.camunda.zeebe.protocol.record.value.ProtectionMode#REDACT} with {@code null}: the value of a
   * variable record, or the input values of a decision evaluation record. Any other record is left
   * untouched.
   *
   * @param sensitiveVariablePatterns the configured patterns, used to find the nested keys to
   *     redact in a variable whose own name is not sensitive
   */
  static void apply(
      final ValueType valueType,
      final UnifiedRecordValue recordValue,
      final List<Pattern> sensitiveVariablePatterns) {
    switch (valueType) {
      case VARIABLE -> redactVariable((VariableRecord) recordValue, sensitiveVariablePatterns);
      case DECISION_EVALUATION -> redactDecisionInputs((DecisionEvaluationRecord) recordValue);
      default -> {}
    }
  }

  private static void redactVariable(
      final VariableRecord variableRecord, final List<Pattern> patterns) {
    if (!variableRecord.shouldBeRedacted()) {
      return;
    }
    if (!isSensitive(variableRecord.getName(), patterns)) {
      final var nestedRedacted = redactNestedKeys(variableRecord.getValueBuffer(), patterns);
      if (nestedRedacted != null) {
        variableRecord.setValue(nestedRedacted);
        return;
      }
    }
    // the name matches, or no nested key could be found again: err towards hiding it all
    variableRecord.setValue(REDACTED_VALUE);
  }

  /** Returns the value with every sensitive key set to null, or null if it has no such key. */
  private static DirectBuffer redactNestedKeys(
      final DirectBuffer value, final List<Pattern> patterns) {
    try {
      final JsonNode root = JSON_MAPPER.readTree(MsgPackConverter.convertToJson(value));
      if (!redactKeys(root, patterns)) {
        return null;
      }
      return new UnsafeBuffer(MsgPackConverter.convertToMsgPack(root.toString()));
    } catch (final JsonProcessingException e) {
      return null;
    }
  }

  private static boolean redactKeys(final JsonNode node, final List<Pattern> patterns) {
    boolean changed = false;
    if (node instanceof final ObjectNode object) {
      for (final String key : copyOf(object.fieldNames())) {
        if (isSensitive(key, patterns)) {
          object.set(key, NullNode.getInstance());
          changed = true;
        } else {
          changed |= redactKeys(object.get(key), patterns);
        }
      }
    } else if (node instanceof final ArrayNode array) {
      for (final JsonNode element : array) {
        changed |= redactKeys(element, patterns);
      }
    }
    return changed;
  }

  private static List<String> copyOf(final java.util.Iterator<String> names) {
    final var copy = new ArrayList<String>();
    names.forEachRemaining(copy::add);
    return copy;
  }

  private static boolean isSensitive(final String name, final List<Pattern> patterns) {
    return patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches());
  }

  private static void redactDecisionInputs(final DecisionEvaluationRecord decisionRecord) {
    for (final var evaluatedDecision : decisionRecord.evaluatedDecisions()) {
      for (final var evaluatedInput : evaluatedDecision.evaluatedInputs()) {
        if (evaluatedInput.shouldBeRedacted()) {
          evaluatedInput.setInputValue(REDACTED_VALUE);
        }
      }
    }
  }
}
