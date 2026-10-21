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
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Redacts the values of protected variables before a record reaches any exporter.
 *
 * <p>Applied once per record in {@link RecordExporter#wrap}, so the Camunda exporter, the RDBMS
 * exporter, the raw-record exporters and any customer-built exporter all receive an already
 * redacted record. Only the decoded copy of the record is rewritten; the log and the engine state
 * keep the plain value.
 *
 * <p>This class only enforces a decision made upstream: the protection modes carried on the record
 * as metadata. Records that carry variables as a side payload (e.g. process instance creation) are
 * not covered yet; support for them is added as further cases of {@link #apply}.
 */
final class VariableRedaction {

  /**
   * The value a redacted value is replaced with: JSON {@code null}. The record keeps its protection
   * modes, so consumers tell a redacted value from a genuine {@code null} by reading them.
   */
  private static final DirectBuffer REDACTED_VALUE =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack("null"));

  private VariableRedaction() {}

  /** Redacts the protected variable values in {@code recordValue}; other records are untouched. */
  static void apply(final ValueType valueType, final UnifiedRecordValue recordValue) {
    switch (valueType) {
      case VARIABLE -> redactVariable((VariableRecord) recordValue);
      default -> {}
    }
  }

  private static void redactVariable(final VariableRecord variableRecord) {
    if (variableRecord.getMetadata().getProtectionModes().contains(ProtectionMode.REDACT)) {
      variableRecord.setValue(REDACTED_VALUE);
    }
  }
}
