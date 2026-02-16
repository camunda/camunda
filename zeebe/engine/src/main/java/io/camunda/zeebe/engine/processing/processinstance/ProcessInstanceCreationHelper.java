/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;
import static io.camunda.zeebe.util.buffer.BufferUtil.wrapString;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.common.ElementActivationBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.processing.identity.authorization.AuthorizationCheckBehavior;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.processing.variable.VariableBehavior;
import io.camunda.zeebe.engine.state.deployment.DeployedProcess;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.TagUtil;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public class ProcessInstanceCreationHelper {
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
  private static final String ERROR_MESSAGE_BUSINESS_ID_ALREADY_EXISTS =
      "Expected to create instance of process with business id '%s', but an instance with this business id already exists for process definition key '%d'";
  private static final Set<BpmnElementType> UNSUPPORTED_ELEMENT_TYPES =
      Set.of(
          BpmnElementType.START_EVENT,
          BpmnElementType.SEQUENCE_FLOW,
          BpmnElementType.BOUNDARY_EVENT,
          BpmnElementType.UNSPECIFIED);
  private static final Either<Rejection, Object> VALID = Either.right(null);

  private final AuthorizationCheckBehavior authCheckBehavior;
  private final ProcessState processState;
  private final VariableBehavior variableBehavior;
  private final ElementActivationBehavior elementActivationBehavior;
  private final ElementInstanceState elementInstanceState;

  public ProcessInstanceCreationHelper(
      final ProcessState processState,
      final ElementInstanceState elementInstanceState,
      final AuthorizationCheckBehavior authCheckBehavior,
      final BpmnBehaviors bpmnBehaviors) {
    this.processState = processState;
    this.elementInstanceState = elementInstanceState;
    this.authCheckBehavior = authCheckBehavior;
    variableBehavior = bpmnBehaviors.variableBehavior();
    elementActivationBehavior = bpmnBehaviors.elementActivationBehavior();
  }

  public Either<Rejection, DeployedProcess> findRelevantProcess(
      final ProcessInstanceCreationRecord record) {
    final DirectBuffer bpmnProcessId = record.getBpmnProcessIdBuffer();

    if (bpmnProcessId.capacity() > 0) {
      if (record.getVersion() >= 0) {
        return getProcess(bpmnProcessId, record.getVersion(), record.getTenantId());
      } else {
        return getProcess(bpmnProcessId, record.getTenantId());
      }
    } else if (record.getProcessDefinitionKey() >= 0) {
      return getProcess(record.getProcessDefinitionKey(), record.getTenantId());
    } else {
      return Either.left(
          new Rejection(RejectionType.INVALID_ARGUMENT, ERROR_MESSAGE_NO_IDENTIFIER_SPECIFIED));
    }
  }

  public ProcessInstanceRecord initProcessInstanceRecord(
      final DeployedProcess process,
      final long processInstanceKey,
      final Set<String> tags,
      final DirectBuffer businessId) {
    return new ProcessInstanceRecord()
        .setBpmnProcessId(process.getBpmnProcessId())
        .setVersion(process.getVersion())
        .setProcessDefinitionKey(process.getKey())
        .setProcessInstanceKey(processInstanceKey)
        .setRootProcessInstanceKey(processInstanceKey)
        .setBpmnElementType(BpmnElementType.PROCESS)
        .setElementId(process.getProcess().getId())
        .setFlowScopeKey(-1)
        .setTenantId(process.getTenantId())
        .setTags(tags)
        .setBusinessId(businessId);
  }

  private Either<Rejection, DeployedProcess> getProcess(
      final DirectBuffer bpmnProcessId, final String tenantId) {
    final DeployedProcess process =
        processState.getLatestProcessVersionByProcessId(bpmnProcessId, tenantId);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(ERROR_MESSAGE_NOT_FOUND_BY_PROCESS, bufferAsString(bpmnProcessId))));
    }
  }

  private Either<Rejection, DeployedProcess> getProcess(
      final DirectBuffer bpmnProcessId, final int version, final String tenantId) {
    final DeployedProcess process =
        processState.getProcessByProcessIdAndVersion(bpmnProcessId, version, tenantId);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND,
              String.format(
                  ERROR_MESSAGE_NOT_FOUND_BY_PROCESS_AND_VERSION,
                  bufferAsString(bpmnProcessId),
                  version)));
    }
  }

  private Either<Rejection, DeployedProcess> getProcess(final long key, final String tenantId) {
    final DeployedProcess process = processState.getProcessByKeyAndTenant(key, tenantId);
    if (process != null) {
      return Either.right(process);
    } else {
      return Either.left(
          new Rejection(
              RejectionType.NOT_FOUND, String.format(ERROR_MESSAGE_NOT_FOUND_BY_KEY, key)));
    }
  }

  public Either<Rejection, DeployedProcess> isAuthorized(
      final TypedRecord<ProcessInstanceCreationRecord> command,
      final DeployedProcess deployedProcess) {
    final var processId = bufferAsString(deployedProcess.getBpmnProcessId());
    final var request =
        AuthorizationRequest.builder()
            .command(command)
            .resourceType(AuthorizationResourceType.PROCESS_DEFINITION)
            .permissionType(PermissionType.CREATE_PROCESS_INSTANCE)
            .tenantId(command.getValue().getTenantId())
            .addResourceId(processId)
            .build();

    final var isAuthorized = authCheckBehavior.isAuthorizedOrInternalCommand(request);
    if (isAuthorized.isRight()) {
      return Either.right(deployedProcess);
    }

    final var rejection = isAuthorized.getLeft();
    final String errorMessage =
        RejectionType.NOT_FOUND.equals(rejection.type())
            ? AuthorizationCheckBehavior.NOT_FOUND_ERROR_MESSAGE.formatted(
                "create an instance of process",
                command.getValue().getProcessDefinitionKey(),
                "such process")
            : rejection.reason();
    return Either.left(new Rejection(rejection.type(), errorMessage));
  }

  public Either<Rejection, DeployedProcess> validateCommand(
      final ProcessInstanceCreationRecord command, final DeployedProcess deployedProcess) {
    final var process = deployedProcess.getProcess();
    final var startInstructions = command.startInstructions();
    final var tags = command.getTags();
    final var businessId = command.getBusinessId();

    return validateHasNoneStartEventOrStartInstructions(process, startInstructions)
        .flatMap(valid -> validateElementsExist(process, startInstructions))
        .flatMap(valid -> validateElementsNotInsideMultiInstance(process, startInstructions))
        .flatMap(valid -> validateElementsNotInsideAdHocSubProcess(process, startInstructions))
        .flatMap(valid -> validateTargetsSupportedElementType(process, startInstructions))
        .flatMap(
            valid -> validateElementNotBelongingToEventBasedGateway(process, startInstructions))
        .flatMap(valid -> validateTags(tags))
        .flatMap(
            valid ->
                validateBusinessIdUniqueness(
                    businessId, deployedProcess.getKey(), command.getTenantId()))
        .map(valid -> deployedProcess);
  }

  public void updateCreationRecord(
      final ProcessInstanceCreationRecord record, final ProcessInstanceRecord processInstance) {
    record
        .setProcessInstanceKey(processInstance.getProcessInstanceKey())
        .setRootProcessInstanceKey(processInstance.getRootProcessInstanceKey())
        .setBpmnProcessId(processInstance.getBpmnProcessId())
        .setVersion(processInstance.getVersion())
        .setProcessDefinitionKey(processInstance.getProcessDefinitionKey());
  }

  public void setVariablesFromDocument(
      final ProcessInstanceRecord processInstance, final DirectBuffer variablesBuffer) {

    variableBehavior.mergeLocalDocument(
        processInstance.getProcessInstanceKey(),
        processInstance.getProcessDefinitionKey(),
        processInstance.getProcessInstanceKey(),
        processInstance.getRootProcessInstanceKey(),
        processInstance.getBpmnProcessIdBuffer(),
        processInstance.getTenantId(),
        variablesBuffer);
  }

  public void activateElementsForStartInstructions(
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions,
      final DeployedProcess process,
      final ProcessInstanceRecord processInstance) {

    startInstructions.forEach(
        instruction -> {
          final var element = process.getProcess().getElementById(instruction.getElementId());
          elementActivationBehavior.activateElement(processInstance, element);
        });
  }

  private Either<Rejection, ?> validateHasNoneStartEventOrStartInstructions(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    if (process.getNoneStartEvent() != null || !startInstructions.isEmpty()) {
      return VALID;
    } else {
      return Either.left(
          new Rejection(RejectionType.INVALID_STATE, ERROR_MESSAGE_NO_NONE_START_EVENT));
    }
  }

  private Either<Rejection, ?> validateElementsExist(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> !isElementOfProcess(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but no element found with id '%s'."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private boolean isElementOfProcess(final ExecutableProcess process, final String elementId) {
    return process.getElementById(wrapString(elementId)) != null;
  }

  private Either<Rejection, ?> validateElementsNotInsideMultiInstance(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> isElementInsideMultiInstance(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but the element with id '%s' is inside a multi-instance subprocess. The creation of elements inside a multi-instance subprocess is not supported."
                            .formatted(elementId))))
        .orElse(VALID);
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

  private Either<Rejection, ?> validateElementsNotInsideAdHocSubProcess(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> isElementInsideAdHocSubProcess(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but the element with id '%s' is inside an ad-hoc subprocess. The creation of elements inside an ad-hoc subprocess is not supported."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private boolean isElementInsideAdHocSubProcess(
      final ExecutableProcess process, final String elementId) {
    final var element = process.getElementById(wrapString(elementId));
    return element != null && hasAdHocSubProcessScope(element);
  }

  private boolean hasAdHocSubProcessScope(final ExecutableFlowElement flowElement) {
    final var flowScope = flowElement.getFlowScope();
    if (flowScope == null) {
      return false;
    }

    if (flowScope.getElementType() == BpmnElementType.AD_HOC_SUB_PROCESS) {
      return true;
    } else {
      return hasAdHocSubProcessScope(flowScope);
    }
  }

  private Either<Rejection, ?> validateTargetsSupportedElementType(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(
            instruction ->
                new ElementIdAndType(
                    instruction.getElementId(),
                    process.getElementById(instruction.getElementIdBuffer()).getElementType()))
        .filter(
            elementIdAndType -> UNSUPPORTED_ELEMENT_TYPES.contains(elementIdAndType.elementType))
        .findAny()
        .map(
            elementIdAndType ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        ("Expected to create instance of process with start instructions but the element with id '%s' targets unsupported element type '%s'. "
                                + "Supported element types are: %s")
                            .formatted(
                                elementIdAndType.elementId,
                                elementIdAndType.elementType,
                                Arrays.stream(BpmnElementType.values())
                                    .filter(
                                        elementType ->
                                            !UNSUPPORTED_ELEMENT_TYPES.contains(elementType))
                                    .collect(Collectors.toSet())))))
        .orElse(VALID);
  }

  private Either<Rejection, ?> validateElementNotBelongingToEventBasedGateway(
      final ExecutableProcess process,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions) {

    return startInstructions.stream()
        .map(ProcessInstanceCreationStartInstruction::getElementId)
        .filter(elementId -> doesElementBelongToAnEventBasedGateway(process, elementId))
        .findAny()
        .map(
            elementId ->
                Either.left(
                    new Rejection(
                        RejectionType.INVALID_ARGUMENT,
                        "Expected to create instance of process with start instructions but the element with id '%s' belongs to an event-based gateway. The creation of elements belonging to an event-based gateway is not supported."
                            .formatted(elementId))))
        .orElse(VALID);
  }

  private Either<Rejection, ?> validateTags(final Set<String> tags) {
    if (tags.size() > TagUtil.MAX_NUMBER_OF_TAGS) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              String.format(
                  "Expected to create instance of process with tags, but the number of tags exceeds the limit of %s.",
                  TagUtil.MAX_NUMBER_OF_TAGS)));
    }

    final List<String> invalidTags = tags.stream().filter(tag -> !TagUtil.isValidTag(tag)).toList();
    if (!invalidTags.isEmpty()) {
      return Either.left(
          new Rejection(
              RejectionType.INVALID_ARGUMENT,
              "Expected to create instance of process with tags, but the tags '%s' are invalid. %s"
                  .formatted(String.join("', '", invalidTags), TagUtil.TAG_FORMAT_DESCRIPTION)));
    }

    return VALID;
  }

  private Either<Rejection, ?> validateBusinessIdUniqueness(
      final String businessId, final long processDefinitionKey, final String tenantId) {
    // If no business id is provided, skip validation
    if (businessId == null || businessId.isEmpty()) {
      return VALID;
    }

    // Check if a process instance with this business id already exists
    if (elementInstanceState.hasActiveProcessInstanceWithBusinessId(
        businessId, processDefinitionKey, tenantId)) {
      return Either.left(
          new Rejection(
              RejectionType.ALREADY_EXISTS,
              String.format(
                  ERROR_MESSAGE_BUSINESS_ID_ALREADY_EXISTS, businessId, processDefinitionKey)));
    }

    return VALID;
  }

  private boolean doesElementBelongToAnEventBasedGateway(
      final ExecutableProcess process, final String elementId) {
    final ExecutableFlowNode element = process.getElementById(elementId, ExecutableFlowNode.class);
    return element.getIncoming().stream()
        .map(ExecutableSequenceFlow::getSource)
        .anyMatch(
            flowNode -> flowNode.getElementType().equals(BpmnElementType.EVENT_BASED_GATEWAY));
  }

  private record ElementIdAndType(String elementId, BpmnElementType elementType) {}
}
