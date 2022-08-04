/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.api.TypedRecord;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecordProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedResponseWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import java.util.List;
import java.util.Optional;
import org.agrona.DirectBuffer;

public final class ProcessInstanceModificationProcessor
    implements TypedRecordProcessor<ProcessInstanceModificationRecord> {

  private final StateWriter stateWriter;
  private final TypedResponseWriter responseWriter;
  private final TypedCommandWriter commandWriter;
  private final KeyGenerator keyGenerator;
  private final ElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceModificationProcessor(
      final Writers writers,
      final KeyGenerator keyGenerator,
      final ElementInstanceState elementInstanceState,
      final ProcessState processState) {
    stateWriter = writers.state();
    responseWriter = writers.response();
    commandWriter = writers.command();
    this.keyGenerator = keyGenerator;
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void processRecord(final TypedRecord<ProcessInstanceModificationRecord> command) {
    final long commandKey = command.getKey();
    final var value = command.getValue();

    // if set, the command's key should take precedence over the processInstanceKey
    final long eventKey = commandKey > -1 ? commandKey : value.getProcessInstanceKey();

    final var processInstance =
        elementInstanceState.getInstance(value.getProcessInstanceKey()).getValue();
    // todo: reject if process instance could not be found
    final var process = processState.getProcessByKey(processInstance.getProcessDefinitionKey());

    value
        .getActivateInstructions()
        .forEach(
            instruction -> {
              final var elementToActivate =
                  process.getProcess().getElementById(instruction.getElementId());

              // todo: reject if elementToActivate could not be found

              activateElement(processInstance, instruction, elementToActivate);
            });

    stateWriter.appendFollowUpEvent(eventKey, ProcessInstanceModificationIntent.MODIFIED, value);

    responseWriter.writeEventOnCommand(
        eventKey, ProcessInstanceModificationIntent.MODIFIED, value, command);
  }

  private void activateElement(
      final ProcessInstanceRecord processInstance,
      final ProcessInstanceModificationActivateInstructionValue instruction,
      final AbstractFlowElement elementToActivate) {
    final var elementInstanceRecord = new ProcessInstanceRecord();
    elementInstanceRecord.wrap(processInstance);
    // todo: deal with non-existing flow scope (#9643)
    final Optional<Long> flowScopeKey = getFlowScopeKey(processInstance, elementToActivate);
    commandWriter.appendFollowUpCommand(
        keyGenerator.nextKey(),
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        elementInstanceRecord
            .setFlowScopeKey(flowScopeKey.get())
            .setBpmnElementType(elementToActivate.getElementType())
            .setElementId(instruction.getElementId())
            .setParentProcessInstanceKey(-1)
            .setParentElementInstanceKey(-1));
  }

  private Optional<Long> getFlowScopeKey(
      final ProcessInstanceRecord processInstance, final AbstractFlowElement elementToActivate) {
    final var flowScope = elementToActivate.getFlowScope();
    if (flowScope.getId().equals(processInstance.getElementIdBuffer())) {
      return Optional.of(processInstance.getProcessInstanceKey());
    } else {
      return getFlowScopeKey(processInstance.getProcessInstanceKey(), flowScope.getId());
    }
  }

  private Optional<Long> getFlowScopeKey(
      final long ancestorKey, final DirectBuffer targetElementId) {
    final List<ElementInstance> children = elementInstanceState.getChildren(ancestorKey);

    for (final ElementInstance child : children) {
      if (child.getValue().getElementIdBuffer().equals(targetElementId)) {
        // todo: instead of early return we should reject the command when multiple matching
        //  children are found
        return Optional.of(child.getKey());
      } else {
        final var optionalFlowScopeKey = getFlowScopeKey(child.getKey(), targetElementId);
        if (optionalFlowScopeKey.isPresent()) {
          return optionalFlowScopeKey;
        }
      }
    }
    return Optional.empty();
  }
}
