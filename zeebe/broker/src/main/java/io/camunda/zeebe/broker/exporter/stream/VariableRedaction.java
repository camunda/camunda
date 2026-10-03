/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import io.camunda.zeebe.broker.system.configuration.DataProtectionCfg;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import java.util.regex.Pattern;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * Proof of concept for permanently redacting sensitive process variables (product-hub #3805).
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
 * <p>The declaration is a regular expression, matched against the variable's full name, sourced
 * from {@code camunda.data.protection.pattern} rather than a hardcoded prefix -- see {@link
 * io.camunda.zeebe.broker.system.configuration.DataProtectionCfg}.
 *
 * <p>PoC limitations, deliberate: only {@link ValueType#VARIABLE} records are covered -- job
 * payloads, process instance creation payloads, DMN decision-instance inputs and message
 * correlation keys carry variable values on other record types and are untouched.
 */
final class VariableRedaction {

  /** The pattern used when no {@code camunda.data.protection.pattern} is configured. */
  static final Pattern DEFAULT_SENSITIVE_VARIABLE_PATTERN =
      Pattern.compile(DataProtectionCfg.DEFAULT_PATTERN);

  /** The value every redacted variable is replaced with, as a JSON string. */
  static final String REDACTION_MARKER = "[REDACTED]";

  private static final DirectBuffer REDACTED_VALUE =
      new UnsafeBuffer(MsgPackConverter.convertToMsgPack("\"" + REDACTION_MARKER + "\""));

  private VariableRedaction() {}

  /**
   * Replaces the value of {@code recordValue} with the redaction marker if it is a variable record
   * whose name fully matches {@code sensitiveVariablePattern}. Any other record is left untouched.
   */
  static void apply(
      final Pattern sensitiveVariablePattern,
      final ValueType valueType,
      final UnifiedRecordValue recordValue) {
    if (valueType != ValueType.VARIABLE) {
      return;
    }

    final var variableRecord = (VariableRecord) recordValue;
    if (isSensitive(sensitiveVariablePattern, variableRecord.getName())) {
      variableRecord.setValue(REDACTED_VALUE);
    }
  }

  static boolean isSensitive(final Pattern sensitiveVariablePattern, final String variableName) {
    return sensitiveVariablePattern.matcher(variableName).matches();
  }
}
