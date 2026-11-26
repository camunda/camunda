/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.cases;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.state.immutable.CaseState;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.value.CaseLifecycleState;
import io.camunda.zeebe.protocol.record.value.CaseStageState;
import io.camunda.zeebe.stream.api.state.KeyGenerator;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

/**
 * Writes the {@link CaseIntent#CREATED} event for a newly created root process instance if its
 * process is marked as a case ({@code zeebe:case}). The case gets a key of its own, which is also
 * the key of the event.
 *
 * <p>The event is written in the same processing step that creates the process instance, before the
 * instance is activated, so the case exists for every event that follows. It carries the full case:
 * state {@link CaseLifecycleState#ACTIVE} and every stage of the process as {@link
 * CaseStageState#NOT_STARTED}.
 *
 * <p>The title is the value of the variable named by the {@code zeebe:case} title, taken from the
 * top-level variables the instance is created with. Only a non-blank string counts; in every other
 * case the title falls back to the process name or process ID. See {@link CaseTitle}.
 */
@NullMarked
public final class CaseBehavior {

  private final StateWriter stateWriter;
  private final KeyGenerator keyGenerator;
  private final CaseState caseState;
  private final CaseRecord caseRecord = new CaseRecord();

  public CaseBehavior(
      final StateWriter stateWriter, final KeyGenerator keyGenerator, final CaseState caseState) {
    this.stateWriter = stateWriter;
    this.keyGenerator = keyGenerator;
    this.caseState = caseState;
  }

  /**
   * Writes the {@link CaseIntent#CREATED} event if the process is a case.
   *
   * @param process the process the instance is created for
   * @param processInstance the record of the new root process instance, in the state it is
   *     activated with; the case takes over its key as process instance key, and its definition,
   *     tenant, business ID and storage ordinal
   * @param creationVariables the variables the instance is created with, to find the title in
   */
  public void createCaseIfMarked(
      final ExecutableProcess process,
      final ProcessInstanceRecord processInstance,
      final DirectBuffer creationVariables) {
    if (!process.isCase()) {
      return;
    }
    final var caseDefinition = Objects.requireNonNull(process.getCaseDefinition());

    final var title =
        CaseTitle.of(
            caseDefinition.titleVariable(), creationVariables, () -> fallbackTitle(process));

    final var caseKey = keyGenerator.nextKey();
    caseRecord.reset();
    caseRecord
        .setCaseKey(caseKey)
        .setTitle(title)
        .setState(CaseLifecycleState.ACTIVE)
        .setProcessInstanceKey(processInstance.getProcessInstanceKey())
        .setProcessDefinitionKey(processInstance.getProcessDefinitionKey())
        .setBpmnProcessId(processInstance.getBpmnProcessId())
        .setTenantId(processInstance.getTenantId())
        .setBusinessId(processInstance.getBusinessId())
        .setStorageOrdinal(processInstance.getStorageOrdinal());
    caseDefinition
        .stages()
        .forEach(
            stage ->
                caseRecord.addStage(stage.elementId(), stage.name(), CaseStageState.NOT_STARTED));
    stateWriter.appendFollowUpEvent(caseKey, CaseIntent.CREATED, caseRecord);
  }

  /**
   * Writes the {@link CaseIntent#BUSINESS_ID_ASSIGNED} event, carrying the full case with the
   * assigned business ID, if the process instance is a case. Does nothing otherwise.
   *
   * @param processInstanceKey the key of the root process instance the business ID is assigned to
   * @param businessId the assigned business ID
   */
  public void assignBusinessId(final long processInstanceKey, final String businessId) {
    final var storedCase = caseState.getCaseByProcessInstanceKey(processInstanceKey);
    if (storedCase == null) {
      return;
    }

    storedCase.setBusinessId(businessId);
    stateWriter.appendFollowUpEvent(
        storedCase.getCaseKey(), CaseIntent.BUSINESS_ID_ASSIGNED, storedCase);
  }

  /** The process name, or the BPMN process ID if the process has no name. */
  private static String fallbackTitle(final ExecutableProcess process) {
    final var name = BufferUtil.bufferAsString(process.getName());
    return name.isBlank() ? BufferUtil.bufferAsString(process.getId()) : name;
  }
}
