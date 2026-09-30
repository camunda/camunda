/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
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
 * <p>{@link ValueType#DECISION_EVALUATION} records are covered the same way: the engine flags each
 * evaluated input whose expression references a sensitive variable, and its input value is redacted
 * here.
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

  /**
   * Replaces every value in {@code recordValue} declared with {@link
   * io.camunda.zeebe.protocol.record.value.ProtectionMode#REDACT} with {@code null}: the value of a
   * variable record, or the input values of a decision evaluation record. Any other record is left
   * untouched.
   */
  static void apply(final ValueType valueType, final UnifiedRecordValue recordValue) {
    switch (valueType) {
      case VARIABLE -> redactVariable((VariableRecord) recordValue);
      case DECISION_EVALUATION -> redactDecisionInputs((DecisionEvaluationRecord) recordValue);
      default -> {}
    }
  }

  private static void redactVariable(final VariableRecord variableRecord) {
    if (variableRecord.shouldBeRedacted()) {
      variableRecord.setValue(REDACTED_VALUE);
    }
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
