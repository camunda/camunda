/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.metrics.ProcessEngineMetrics;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContextImpl;
import io.camunda.zeebe.engine.processing.common.CatchEventBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCatchEventSupplier;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.streamprocessor.CommandProcessor;
import io.camunda.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.camunda.zeebe.engine.processing.streamprocessor.sideeffect.SideEffectQueue;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.TypedCommandWriter;
import io.camunda.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.KeyGenerator;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.HashMap;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class CreateProcessInstanceProcessor
    implements CommandProcessor<ProcessInstanceCreationRecord> {

  private static final String ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED =
      "Expected at least a bpmnProcessId or a key greater than -1, but none given";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS =
      "Expected to find process definition with process ID '%s', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION =
      "Expected to find process definition with process ID '%s' and version '%d', but none found";
  private static final String ERROR_MESSAGE_NOT_FOUND_BY_KEY =
      "Expected to find process definition with key '%d', but none found";
  private static final String ERROR_MESSAGE_NO_NONE_START_EVENT =
      "Expected to create instance of process with none start event, but there is no such event";

  private final ProcessInstanceRecord newProcessInstance = new ProcessInstanceRecord();

  private final SideEffectQueue sideEffectQueue = new SideEffectQueue();

  private final ProcessState processState;
  private final VariableBehavior variableBehavior;

  private final CatchEventBehavior catchEventBehavior;

  private final KeyGenerator keyGenerator;
  private final TypedCommandWriter commandWriter;
  private final StateWriter stateWriter;
  private final ProcessEngineMetrics metrics;

  public CreateProcessInstanceProcessor(
      final ProcessState processState,
      final KeyGenerator keyGenerator,
      final Writers writers,
      final VariableBehavior variableBehavior,
      final CatchEventBehavior catchEventBehavior,
      final ProcessEngineMetrics metrics) {
    this.processState = processState;
    this.variableBehavior = variableBehavior;
    this.catchEventBehavior = catchEventBehavior;
    this.keyGenerator = keyGenerator;
    commandWriter = writers.command();
    stateWriter = writers.state();
    this.metrics = metrics;
  }

  @Override
  public boolean onCommand(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final CommandControl<ProcessInstanceCreationRecord> controller) {
    // cleanup side effects from previous command
    sideEffectQueue.clear();

    final ProcessInstanceCreationRecord record = command.getValue();
    final DeployedProcess process = getProcess(record, controller);
    if (process == null
        || !hasNoneStartEventOrStartInstructions(controller, process, record.startInstructions())
        || !hasValidStartInstructions(
            controller, process.getProcess(), record.startInstructions())) {
      return true;
    }

    final long processInstanceKey = keyGenerator.nextKey();

    setVariablesFromDocument(
        record, process.getKey(), processInstanceKey, process.getBpmnProcessId());

    final var processInstance = initProcessInstanceRecord(process, processInstanceKey);
    if (record.startInstructions().isEmpty()) {
      commandWriter.appendFollowUpCommand(
          processInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, processInstance);
    } else {
      activateElementsForStartInstructions(
          record.startInstructions(), process, processInstanceKey, processInstance);
    }

    record
        .setProcessInstanceKey(processInstanceKey)
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey());
    controller.accept(ProcessInstanceCreationIntent.CREATED, record);
    metrics.processInstanceCreated(record);
    return true;
  }

  private boolean hasNoneStartEventOrStartInstructions(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final DeployedProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {
    if (process.getProcess().getNoneStartEvent() == null && startInstructions.isEmpty()) {
      controller.reject(RejectionType.INVALID_STATE, ERROR_MESSAGE_NO_NONE_START_EVENT);
      return false;
    }

    return true;
  }

  private boolean hasValidStartInstructions(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return !hasStartInstructionsWithNonExistingElements(controller, process, startInstructions)
        && !hasStartInstructionsWithMultiInstanceElement(controller, process, startInstructions);
  }

  private boolean hasStartInstructionsWithNonExistingElements(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> !isElementOfProcess(process, elementId))
        .findAny()
        .map(
            elementId -> {
              controller.reject(
                  RejectionType.INVALID_ARGUMENT,
                  "Expected to create instance of process with start instructions but no element found with id '%s'."
                      .formatted(elementId));
              return true;
            })
        .orElse(false);
  }

  private boolean isElementOfProcess(final ExecutableProcess process, final String elementId) {
    return process.getElementById(wrapString(elementId)) != null;
  }

  private boolean hasStartInstructionsWithMultiInstanceElement(
      final CommandControl<ProcessInstanceCreationRecord> controller,
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> isElementInsideMultiInstance(process, elementId))
        .findAny()
        .map(
            elementId -> {
              controller.reject(
                  RejectionType.INVALID_ARGUMENT,
                  "Expected to create instance of process with start instructions but the element with id '%s' is inside a multi-instance subprocess. The creation of elements inside a multi-instance subprocess is not supported."
                      .formatted(elementId));
              return true;
            })
        .orElse(false);
  }

  private boolean isElementInsideMultiInstance(
      final ExecutableProcess process, final String elementId) {
    final var element = process.getElementById(wrapString(elementId));
    return element != null && hasMultiInstanceScope(element);
  }

  private boolean hasMultiInstanceScope(final ExecutableFlowElement flowElement) {
    final var flowScope = flowElement.getFlowScope();
    if (flowScope == null) {
      return false;
    }

    if (flowScope.getElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      return true;
    } else {
      return hasMultiInstanceScope(flowScope);
    }
  }

  private void setVariablesFromDocument(
      final ProcessInstanceCreationRecord record,
      final long processDefinitionKey,
      final long processInstanceKey,
      final DirectBuffer bpmnProcessId) {

    variableBehavior.mergeLocalDocument(
        processInstanceKey,
        processDefinitionKey,
        processInstanceKey,
        bpmnProcessId,
        record.getVariablesBuffer());
  }

  private ProcessInstanceRecord initProcessInstanceRecord(
      final DeployedProcess process, final long processInstanceKey) {
    newProcessInstance.reset();
    newProcessInstance.setBpmnProcessId(process.getBpmnProcessId());
    newProcessInstance.setVersion(process.getVersion());
    newProcessInstance.setProcessDefinitionKey(process.getKey());
    newProcessInstance.setProcessInstanceKey(processInstanceKey);
    newProcessInstance.setBpmnElementType(BpmnElementType.PROCESS);
    newProcessInstance.setElementId(process.getProcess().getId());
    newProcessInstance.setFlowScopeKey(-1);
    return newProcessInstance;
  }

  private DeployedProcess getProcess(
      final ProcessInstanceCreationRecord record, final CommandControl controller) {
    final DeployedProcess process;

    final DirectBuffer bpmnProcessId = record.getBpmnProcessIdBuffer();

    if (bpmnProcessId.capacity() > 0) {
      if (record.getVersion() >= 0) {
        process = getProcess(bpmnProcessId, record.getVersion(), controller);
      } else {
        process = getProcess(bpmnProcessId, controller);
      }
    } else if (record.getProcessDefinitionKey() >= 0) {
      process = getProcess(record.getProcessDefinitionKey(), controller);
    } else {
      controller.reject(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED);
      process = null;
    }

    return process;
  }

  private DeployedProcess getProcess(
      final DirectBuffer bpmnProcessId, final CommandControl controller) {
    final DeployedProcess process = processState.getLatestProcessVersionByProcessId(bpmnProcessId);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(ERROR_MESSAGE_NOT_FOUND_BY_PROCESS, bufferAsString(bpmnProcessId)));
    }

    return process;
  }

  private DeployedProcess getProcess(
      final DirectBuffer bpmnProcessId, final int version, final CommandControl controller) {
    final DeployedProcess process =
        processState.getProcessByProcessIdAndVersion(bpmnProcessId, version);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND,
          String.format(
              ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION,
              bufferAsString(bpmnProcessId),
              version));
    }

    return process;
  }

  private DeployedProcess getProcess(final long key, final CommandControl controller) {
    final DeployedProcess process = processState.getProcessByKey(key);
    if (process == null) {
      controller.reject(
          RejectionType.NOT_FOUND, String.format(ERROR_MESSAGE_NOT_FOUND_BY_KEY, key));
    }

    return process;
  }

  private void activateElementsForStartInstructions(
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions,
      final DeployedProcess process,
      final long processInstanceKey,
      final ProcessInstanceRecord processInstance) {

    activateElementInstance(process.getProcess(), processInstanceKey, processInstance);

    final Map<DirectBuffer, Long> activatedFlowScopeIds = new HashMap<>();
    activatedFlowScopeIds.put(processInstance.getElementIdBuffer(), processInstanceKey);

    startInstructions.forEach(
        instruction -> {
          final DirectBuffer elementId = wrapString(instruction.getElementId());
          final long flowScopeKey =
              activateFlowScopes(process, processInstanceKey, elementId, activatedFlowScopeIds);

          final long elementInstanceKey = keyGenerator.nextKey();
          final ProcessInstanceRecord elementRecord =
              createProcessInstanceRecord(process, processInstanceKey, elementId, flowScopeKey);
          commandWriter.appendFollowUpCommand(
              elementInstanceKey, ProcessInstanceIntent.ACTIVATE_ELEMENT, elementRecord);
        });

    // applying the side effects is part of creating the event subscriptions
    sideEffectQueue.flush();
  }

  /**
   * Activates the flow scopes of a given element id. This is used when starting a process instance
   * at a different place than the start event.
   *
   * <p>The method uses recursion to go from the desired start element to the highest flow scope of
   * the process. This will always be the flow scope of the entire process. At this stage the
   * process is already activated, so the activation for this is skipped (it is present in the
   * activatedFlowScopeIds map). From here it traverses the flow scopes back down to the desired
   * start element and activates all flow scopes in order. The desired start element is not
   * activated here as an activate command is sent for this later on.
   *
   * <p>To prevent activating the same element multiple times a map of activatedFlowScopeIds will
   * keep track of which elements have been activated and the key of the element instance.
   *
   * @param process The deployed process
   * @param processInstanceKey The process instance key
   * @param elementId The desired start element id
   * @param activatedFlowScopeIds The elements that have already been activated
   * @return The latest activated flow scope key
   */
  private long activateFlowScopes(
      final DeployedProcess process,
      final long processInstanceKey,
      final DirectBuffer elementId,
      final Map<DirectBuffer, Long> activatedFlowScopeIds) {
    final ExecutableFlowElement flowScope =
        process.getProcess().getElementById(elementId).getFlowScope();

    if (activatedFlowScopeIds.containsKey(flowScope.getId())) {
      return activatedFlowScopeIds.get(flowScope.getId());
    } else {
      final long flowScopeKey =
          activateFlowScopes(process, processInstanceKey, flowScope.getId(), activatedFlowScopeIds);
      final ProcessInstanceRecord flowScopeRecord =
          createProcessInstanceRecord(process, processInstanceKey, flowScope.getId(), flowScopeKey);

      final long elementInstanceKey = keyGenerator.nextKey();
      activatedFlowScopeIds.put(flowScope.getId(), elementInstanceKey);

      activateElementInstance(flowScope, elementInstanceKey, flowScopeRecord);

      return elementInstanceKey;
    }
  }

  private void activateElementInstance(
      final ExecutableFlowElement element,
      final long elementInstanceKey,
      final ProcessInstanceRecord elementRecord) {

    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATING, elementRecord);
    stateWriter.appendFollowUpEvent(
        elementInstanceKey, ProcessInstanceIntent.ELEMENT_ACTIVATED, elementRecord);

    createEventSubscriptions(element, elementRecord, elementInstanceKey);
  }

  /**
   * Create the event subscriptions of the given element. Assuming that the element instance is in
   * state ACTIVATED.
   */
  private void createEventSubscriptions(
      final ExecutableFlowElement element,
      final ProcessInstanceRecord elementRecord,
      final long elementInstanceKey) {

    if (element instanceof ExecutableCatchEventSupplier catchEventSupplier) {
      final var bpmnElementContext = new BpmnElementContextImpl();
      bpmnElementContext.init(
          elementInstanceKey, elementRecord, ProcessInstanceIntent.ELEMENT_ACTIVATED);

      catchEventBehavior.subscribeToEvents(
          bpmnElementContext, catchEventSupplier, sideEffectQueue, commandWriter);
    }
  }

  private ProcessInstanceRecord createProcessInstanceRecord(
      final DeployedProcess process,
      final long processInstanceKey,
      final DirectBuffer elementId,
      final long flowScopeKey) {
    final ProcessInstanceRecord record = new ProcessInstanceRecord();
    record.setBpmnProcessId(process.getBpmnProcessId());
    record.setVersion(process.getVersion());
    record.setProcessDefinitionKey(process.getKey());
    record.setProcessInstanceKey(processInstanceKey);
    record.setBpmnElementType(process.getProcess().getElementById(elementId).getElementType());
    record.setElementId(elementId);
    record.setFlowScopeKey(flowScopeKey);
    return record;
  }
}
