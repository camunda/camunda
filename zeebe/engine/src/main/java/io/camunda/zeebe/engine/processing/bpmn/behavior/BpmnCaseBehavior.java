/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.behavior;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCase;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.cases.CaseRecord;
import io.camunda.zeebe.protocol.record.intent.CaseIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.CaseStageStatus;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;

/**
 * Writes {@code CASE} events for root process instances of processes marked with {@code
 * zeebe:case}, derived from the lifecycle transitions of the process and its stages.
 */
public final class BpmnCaseBehavior {

  private static final Set<ProcessInstanceIntent> RELEVANT_TRANSITIONS =
      Set.of(
          ProcessInstanceIntent.ELEMENT_ACTIVATED,
          ProcessInstanceIntent.ELEMENT_COMPLETED,
          ProcessInstanceIntent.ELEMENT_TERMINATED);

  private static final Set<BpmnElementType> RELEVANT_TYPES =
      Set.of(
          BpmnElementType.PROCESS,
          BpmnElementType.SUB_PROCESS,
          BpmnElementType.EVENT_SUB_PROCESS,
          BpmnElementType.AD_HOC_SUB_PROCESS);

  private final BpmnStateBehavior stateBehavior;
  private final StateWriter stateWriter;

  public BpmnCaseBehavior(final BpmnStateBehavior stateBehavior, final StateWriter stateWriter) {
    this.stateBehavior = stateBehavior;
    this.stateWriter = stateWriter;
  }

  public void onTransition(
      final BpmnElementContext context, final ProcessInstanceIntent transition) {
    if (!RELEVANT_TRANSITIONS.contains(transition)
        || !RELEVANT_TYPES.contains(context.getBpmnElementType())
        || context.getParentProcessInstanceKey() > 0) {
      return;
    }

    final var caseDefinition =
        stateBehavior
            .getProcess(context.getProcessDefinitionKey(), context.getTenantId())
            .map(process -> process.getProcess().getCaseDefinition())
            .orElse(null);
    if (caseDefinition == null) {
      return;
    }

    final long processInstanceKey = context.getProcessInstanceKey();
    final var record = baseRecord(context);

    if (context.getBpmnElementType() == BpmnElementType.PROCESS) {
      switch (transition) {
        case ELEMENT_ACTIVATED -> {
          record.setTitle(readTitle(context, caseDefinition));
          caseDefinition.stages().forEach(stage -> addStage(record, stage));
          caseDefinition.startElementIds().forEach(record::addStartElementId);
          stateWriter.appendFollowUpEvent(processInstanceKey, CaseIntent.CREATED, record);
        }
        case ELEMENT_COMPLETED ->
            stateWriter.appendFollowUpEvent(processInstanceKey, CaseIntent.COMPLETED, record);
        case ELEMENT_TERMINATED ->
            stateWriter.appendFollowUpEvent(processInstanceKey, CaseIntent.TERMINATED, record);
        default -> {}
      }
      return;
    }

    if (context.getFlowScopeKey() != processInstanceKey) {
      return;
    }
    final var stage = caseDefinition.getStage(BufferUtil.bufferAsString(context.getElementId()));
    if (stage.isEmpty()) {
      return;
    }
    final var status =
        switch (transition) {
          case ELEMENT_ACTIVATED -> CaseStageStatus.ACTIVE;
          case ELEMENT_COMPLETED -> CaseStageStatus.COMPLETED;
          default -> CaseStageStatus.TERMINATED;
        };
    record.addStage(stage.get().elementId(), status);
    stateWriter.appendFollowUpEvent(processInstanceKey, CaseIntent.STAGE_UPDATED, record);
  }

  private static CaseRecord baseRecord(final BpmnElementContext context) {
    final var businessId = context.getBusinessId();
    return new CaseRecord()
        .setProcessInstanceKey(context.getProcessInstanceKey())
        .setProcessDefinitionKey(context.getProcessDefinitionKey())
        .setBpmnProcessId(BufferUtil.bufferAsString(context.getBpmnProcessId()))
        .setTenantId(context.getTenantId())
        .setBusinessId(businessId == null ? "" : businessId)
        .setStorageOrdinal(context.getStorageOrdinal());
  }

  private static void addStage(
      final CaseRecord record, final ExecutableCase.ExecutableStage stage) {
    record.addStage(
        stage.elementId(),
        stage.name(),
        stage.alternative(),
        stage.nextElementIds(),
        CaseStageStatus.NOT_STARTED);
  }

  private String readTitle(final BpmnElementContext context, final ExecutableCase caseDefinition) {
    final var titleVariable = caseDefinition.titleVariable();
    if (titleVariable == null) {
      return "";
    }
    final var value = stateBehavior.getLocalVariable(context, BufferUtil.wrapString(titleVariable));
    if (value == null || value.capacity() == 0) {
      return "";
    }
    // the buffer is a view into the state; convert immediately
    final var title = MsgPackConverter.convertToObject(value, Object.class);
    return title == null ? "" : String.valueOf(title);
  }
}
