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
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.variable.IndexedDocument;
import io.camunda.zeebe.msgpack.spec.MsgPackCodes;
import io.camunda.zeebe.msgpack.spec.MsgPackReader;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Writes the {@link CaseIntent#CREATED} event for a newly created root process instance if its
 * process is marked as a case ({@code zeebe:case}). The key of the case is the key of the process
 * instance, as a case has exactly one root process instance.
 *
 * <p>The event is written in the same processing step that creates the process instance, before the
 * instance is activated. This way the case exists for every event that follows, for example the
 * stage events written when the instance is started with start instructions.
 *
 * <p>The case title is the value of the variable named by the {@code zeebe:case} title, taken from
 * the variables the instance is created with. A string, number or boolean value is used in its
 * textual form. If the variable is missing, an empty string, null, an object or an array, the
 * process instance key is used instead.
 */
@NullMarked
public final class CaseCreationBehavior {

  private final StateWriter stateWriter;
  private final CaseRecord caseRecord = new CaseRecord();
  private final IndexedDocument indexedDocument = new IndexedDocument();
  private final MsgPackReader reader = new MsgPackReader();

  public CaseCreationBehavior(final StateWriter stateWriter) {
    this.stateWriter = stateWriter;
  }

  /**
   * Writes the {@link CaseIntent#CREATED} event if the process is a case.
   *
   * @param process the process the instance is created for
   * @param processInstance the record of the new root process instance, in the state it is
   *     activated with; the case takes over its key, definition, tenant, business ID and storage
   *     ordinal
   * @param creationVariables the variables the instance is created with, to find the title in
   */
  public void createCaseIfMarked(
      final ExecutableProcess process,
      final ProcessInstanceRecord processInstance,
      final DirectBuffer creationVariables) {
    if (!process.isCase()) {
      return;
    }

    final var titleVariable = requireNonNull(process.getCaseProperties()).getTitleVariable();
    String title = null;
    if (titleVariable != null) {
      title = findTitle(creationVariables, titleVariable);
    }
    final var processInstanceKey = processInstance.getProcessInstanceKey();
    if (title == null) {
      title = String.valueOf(processInstanceKey);
    }

    caseRecord.reset();
    caseRecord
        .setProcessInstanceKey(processInstanceKey)
        .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
        .setBpmnProcessId(processInstance.getBpmnProcessId())
        .setTenantId(processInstance.getTenantId())
        .setBusinessId(processInstance.getBusinessId())
        .setTitle(title)
        .setStorageOrdinal(processInstance.getStorageOrdinal());
    stateWriter.appendFollowUpEvent(processInstanceKey, CaseIntent.CREATED, caseRecord);
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
