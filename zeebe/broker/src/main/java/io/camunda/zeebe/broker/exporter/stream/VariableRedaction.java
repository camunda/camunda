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
 * <p>PoC limitations, deliberate: only {@link ValueType#VARIABLE} records are covered -- job
 * payloads, process instance creation payloads, DMN decision-instance inputs and message
 * correlation keys carry variable values on other record types and are untouched.
 */
final class VariableRedaction {

  /** The value every redacted variable is replaced with, as a JSON string. */
  static final String REDACTION_MARKER = "[REDACTED]";

  private static final DirectBuffer REDACTED_VALUE =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + REDACTION_MARKER + "\""));

  private VariableRedaction() {}

  /**
   * Replaces the value of {@code recordValue} with the redaction marker if it is a variable record
   * declared with {@link io.camunda.zeebe.protocol.record.value.ProtectionMode#REDACT}. Any other
   * record is left untouched.
   */
  static void apply(final ValueType valueType, final UnifiedRecordValue recordValue) {
    if (valueType != ValueType.VARIABLE) {
      return;
    }

    final var variableRecord = (VariableRecord) recordValue;
    if (variableRecord.shouldBeRedacted()) {
      variableRecord.setValue(REDACTED_VALUE);
    }
  }
}
