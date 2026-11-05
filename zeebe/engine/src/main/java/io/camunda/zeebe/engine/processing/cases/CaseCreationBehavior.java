/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.state.variable.IndexedDocument;
import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Writes the {@link CaseIntent#CREATE} command for a newly created root process instance if its
 * process is marked as a case ({@code zeebe:case}).
 *
 * <p>The command must be appended after the command that activates the process element instance, so
 * that the process instance already exists when the command is processed.
 *
 * <p>The case title is the value of the variable named by the {@code zeebe:case} title, taken from
 * the variables the instance is created with. A string, number or boolean value is used in its
 * textual form. If the variable is missing, an empty string, null, an object or an array, the
 * process instance key is used instead.
 */
@NullMarked
public final class CaseCreationBehavior {

  private final TypedCommandWriter commandWriter;
  private final CaseRecord caseRecord = new CaseRecord();
  private final IndexedDocument indexedDocument = new IndexedDocument();
  private final MsgPackReader reader = new MsgPackReader();

  public CaseCreationBehavior(final TypedCommandWriter commandWriter) {
    this.commandWriter = commandWriter;
  }

  public void createCaseIfMarked(
      final ExecutableProcess process,
      final long processInstanceKey,
      final DirectBuffer creationVariables) {
    if (!process.isCase()) {
      return;
    }

    final var titleVariable = requireNonNull(process.getCaseProperties()).getTitleVariable();
    String title = null;
    if (titleVariable != null) {
      title = findTitle(creationVariables, titleVariable);
    }
    if (title == null) {
      title = String.valueOf(processInstanceKey);
    }

    caseRecord.setProcessInstanceKey(processInstanceKey).setTitle(title);
    commandWriter.appendFollowUpCommand(processInstanceKey, CaseIntent.CREATE, caseRecord);
  }

  private @Nullable String findTitle(final DirectBuffer document, final DirectBuffer name) {
    if (document.capacity() == 0 || !MsgPackCodes.isMap(document.getByte(0))) {
      return null;
    }

    indexedDocument.index(document);
    for (final var entry : indexedDocument) {
      if (!entry.getName().equals(name)) {
        continue;
      }

      final var value = entry.getValue();
      reader.wrap(value, 0, value.capacity());
      final var token = reader.readToken();
      return switch (token.getType()) {
        case STRING ->
            token.getValueBuffer().capacity() > 0
                ? BufferUtil.bufferAsString(token.getValueBuffer())
                : null;
        case INTEGER -> Long.toString(token.getIntegerValue());
        case FLOAT -> Double.toString(token.getFloatValue());
        case BOOLEAN -> Boolean.toString(token.getBooleanValue());
        default -> null;
      };
    }
    return null;
  }
}
