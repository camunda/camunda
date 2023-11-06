/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.deployment.model.validation;

import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableMultiInstanceBody;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.protocol.record.value.BpmnEventType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.agrona.DirectBuffer;

public final class UnsupportedMultiTenantFeaturesValidator {

  private static final EnumSet<BpmnElementType> REJECTED_ELEMENT_TYPES =
      EnumSet.noneOf(BpmnElementType.class);
  private static final Set<BpmnEventType> UNSUPPORTED_EVENT_TYPES = Set.of();

  /**
   * Validates a list of processes for containing unsupported elements when used with multi-tenancy.
   * Not all features are available for multi-tenancy yet. While multi-tenancy is enabled, all
   * features are available for the default tenant.
   *
   * @param resource the resource file that's getting validated
   * @param executableProcesses the list of processes in this resource
   * @param tenantId the identifier of the tenant that owns the processes
   * @return an Either of which the left contains the failure message if any unsupported elements
   *     are found and the owning tenant is non-default.
   */
  public static Either<Failure, ?> validate(
      final DeploymentResource resource,
      final List<ExecutableProcess> executableProcesses,
      final String tenantId) {

    if (Objects.equals(tenantId, TenantOwned.DEFAULT_TENANT_IDENTIFIER)) {
      // All elements are supported when the default tenant is used
      return Either.right(null);
    }

    final List<Failure> failures = new ArrayList<>();
    executableProcesses.forEach(
        executableProcess ->
            hasUnsupportedElement(resource, executableProcess).ifLeft(failures::add));

    if (failures.isEmpty()) {
      return Either.right(null);
    } else {
      final StringWriter writer = new StringWriter();
      failures.forEach(failure -> writer.write(failure.getMessage()));
      return Either.left(new Failure(writer.toString()));
    }
  }

  /**
   * Checks a given process for any elements that are unsupported with multi-tenancy.
   *
   * @param resource the resource file that's getting validated
   * @param executableProcess the process we are validating
   * @return an Either of which the left contains the failure message if any unsupported elements
   *     have been found
   */
  private static Either<Failure, ?> hasUnsupportedElement(
      final DeploymentResource resource, final ExecutableProcess executableProcess) {
    final var unsupportedElementsInProcess = findUnsupportedElementsInProcess(executableProcess);

    if (unsupportedElementsInProcess.isEmpty()) {
      return Either.right(null);
    }

    final String failureMessage =
        createFailureMessage(resource, executableProcess, unsupportedElementsInProcess);
    return Either.left(new Failure(failureMessage));
  }

  /**
   * Finds all elements in a process that are unsupported with multi-tenancy.
   *
   * @param executableProcess the process
   * @return a list of all unsupported elements in the given process
   */
  private static List<ExecutableFlowNode> findUnsupportedElementsInProcess(
      final ExecutableProcess executableProcess) {

    return executableProcess.getFlowElements().stream()
        .map(
            flowElement ->
                flowElement instanceof ExecutableMultiInstanceBody
                    ? ((ExecutableMultiInstanceBody) flowElement).getInnerActivity()
                    : flowElement)
        .filter(
            flowElement ->
                REJECTED_ELEMENT_TYPES.contains(flowElement.getElementType())
                    || UNSUPPORTED_EVENT_TYPES.contains(flowElement.getEventType()))
        .map(ExecutableFlowNode.class::cast)
        .sorted(Comparator.comparing(ExecutableFlowNode::getId))
        .toList();
  }

  /**
   * Create a descriptive failure message which will be part of the rejection message. It mentions
   * which resource and process contain the issue. It also lists information about the elements that
   * are unsupported.
   *
   * @param resource the resource file that we've validated for loops
   * @param executableProcess the process that we've validated for loops
   * @param unsupportedElements a list of unsupported elements.
   * @return a descriptive failure message
   */
  private static String createFailureMessage(
      final DeploymentResource resource,
      final ExecutableProcess executableProcess,
      final List<ExecutableFlowNode> unsupportedElements) {

    final List<ElementInfo> unsupportedElementsInfo =
        unsupportedElements.stream().map(ElementInfo::new).toList();
    final var failureMessage =
        """
        Processes belonging to custom tenants are not allowed to contain elements unsupported with multi-tenancy. \
        Only the default tenant '<default>' supports these elements currently: %s. \
        See https://github.com/camunda/zeebe/issues/12653 for more details."""
            .formatted(String.join(" > ", unsupportedElementsInfo.toString()));
    return createFormattedFailureMessage(resource, executableProcess, failureMessage);
  }

  private static String createFormattedFailureMessage(
      final DeploymentResource resource,
      final ExecutableProcess executableProcess,
      final String message) {
    final StringWriter writer = new StringWriter();
    writer.write(
        String.format(
            "`%s`: - Process: %s",
            resource.getResourceName(), BufferUtil.bufferAsString(executableProcess.getId())));
    writer.write("\n");
    writer.write("    - ERROR: ");
    writer.write(message);
    writer.write("\n");
    return writer.toString();
  }

  record ElementInfo(DirectBuffer id, BpmnElementType elementType, BpmnEventType eventType) {
    public ElementInfo(final AbstractFlowElement element) {
      this(element.getId(), element.getElementType(), element.getEventType());
    }

    @Override
    public String toString() {
      final StringBuilder builder = new StringBuilder();
      builder.append("'%s'".formatted(BufferUtil.bufferAsString(id)));

      if (eventType == null || eventType == BpmnEventType.UNSPECIFIED) {
        builder.append(" of type '%s'".formatted(elementType));
      } else {
        builder.append(" of type '%s' '%s'".formatted(eventType, elementType));
      }

      return builder.toString();
    }
  }
}
