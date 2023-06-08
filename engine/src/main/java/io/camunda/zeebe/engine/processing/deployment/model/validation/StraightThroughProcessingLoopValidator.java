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
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentResource;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

public final class StraightThroughProcessingLoopValidator {

  private static final EnumSet<BpmnElementType> STRAIGHT_THROUGH_PROCESSING_ELEMENT_TYPES =
      EnumSet.of(
          BpmnElementType.MANUAL_TASK,
          BpmnElementType.TASK,
          BpmnElementType.EXCLUSIVE_GATEWAY,
          BpmnElementType.INCLUSIVE_GATEWAY,
          BpmnElementType.PARALLEL_GATEWAY);
  private static final EnumSet<BpmnElementType> WHITELISTED_ELEMENT_TYPES =
      EnumSet.of(
          BpmnElementType.EXCLUSIVE_GATEWAY,
          BpmnElementType.INCLUSIVE_GATEWAY,
          BpmnElementType.PARALLEL_GATEWAY);

  /**
   * Validates a list of processes for straight-through processing loops. These are loops of
   * elements that don't have any execution logic, such as undefined tasks and manual tasks.
   *
   * @param resource the resource file that's getting validated
   * @param executableProcesses the list of processes in this resource
   * @return an Either of which the left contains the failure message if any loops have been found
   */
  public static Either<Failure, ?> validate(
      final DeploymentResource resource, final List<ExecutableProcess> executableProcesses) {

    final List<Failure> failures = new ArrayList<>();
    for (final ExecutableProcess executableProcess : executableProcesses) {
      hasStraightThroughProcessingLoop(resource, executableProcess).ifLeft(failures::add);
    }

    if (failures.isEmpty()) {
      return Either.right(null);
    } else {
      final StringWriter writer = new StringWriter();
      failures.forEach(failure -> writer.write(failure.getMessage()));
      return Either.left(new Failure(writer.toString()));
    }
  }

  /**
   * Checks a given process for any straight-through processing loops.
   *
   * <p>Note that once we've found a loop we don't keep searching to see if there are more!
   *
   * @param resource the resource file that's getting validated
   * @param executableProcess the process we are validating
   * @return an Either of which the left contains the failure message if any loops have been found
   */
  private static Either<Failure, ?> hasStraightThroughProcessingLoop(
      final DeploymentResource resource, final ExecutableProcess executableProcess) {
    final var straightThroughElements = getStraightThroughElementsInProcess(executableProcess);

    for (final ExecutableFlowNode element : straightThroughElements) {
      final var potentialLoop = new LinkedList<ExecutableFlowNode>();
      final var result = checkForStraightThroughProcessingLoop(potentialLoop, element);
      if (result.isLeft()) {
        final String failureMessage =
            createFailureMessage(resource, executableProcess, result.getLeft());
        return Either.left(new Failure(failureMessage));
      }
    }

    return Either.right(null);
  }

  /**
   * When looking for straight-through processing loops it is not necessary to check in all places
   * of the process. We only have to check the path of straight-through elements. This method
   * returns a list of all the straight-through elements in a process.
   *
   * @param executableProcess the process
   * @return a list of all straight-through elements in the given process
   */
  private static List<ExecutableFlowNode> getStraightThroughElementsInProcess(
      final ExecutableProcess executableProcess) {

    return executableProcess.getFlowElements().stream()
        .filter(
            flowElement ->
                STRAIGHT_THROUGH_PROCESSING_ELEMENT_TYPES.contains(flowElement.getElementType())
                    && !WHITELISTED_ELEMENT_TYPES.contains(flowElement.getElementType()))
        .map(ExecutableFlowNode.class::cast)
        .toList();
  }

  /**
   * This method is the part that actually tries to find any loops. It takes a LinkedList in which
   * it keeps track of elements that are part of the (potential) loop. It also takes an element
   * which is used to detect if we are in a loop.
   *
   * <p>Upon entering this method there are 3 possibilities:
   *
   * <ul>
   *   <li>This element is part of the potentialLoop list. This means that we have passed this
   *       element before. As a result we can conclude that we are in a loop.
   *   <li>The element is not part of the potentialLoop list. We haven't passed the element before
   *       so we are not in a loop yet. However, the element is a straight-through processing
   *       element. We must check the outgoing sequence flow of this element to see if it loops back
   *       around to an element that is part of the potentialLoop list.
   *   <li>We have not encountered this element before, nor is it a straight-through processing
   *       element. We can conclude that this path is not part of a straight-through processing
   *       loop.
   * </ul>
   *
   * @param potentialLoop a list which keeps track of elements that are part of a potential loop
   * @param element the element used to determine if we reached a loop, or are definitely not part
   *     of a loop
   * @return an Either of which the left contains a list of the straight-through processing loop.
   */
  private static Either<List<ExecutableFlowNode>, ?> checkForStraightThroughProcessingLoop(
      final LinkedList<ExecutableFlowNode> potentialLoop, final ExecutableFlowNode element) {

    if (foundLoop(potentialLoop, element)) {
      // It could happen that we detect a loop which doesn't involve the first element we checked.
      // By taking a sublist we make sure we've isolated the loop.
      final var loop = potentialLoop.subList(potentialLoop.indexOf(element), potentialLoop.size());
      // This element is added to the loop twice. This is useful when creating an error message
      // describing the loop.
      loop.add(element);
      return Either.left(loop);
    } else if (STRAIGHT_THROUGH_PROCESSING_ELEMENT_TYPES.contains(element.getElementType())) {
      // We are not in a loop yet, but the element is a straight-through processing element. We must
      // keep checking for loops by analysing the outgoing sequence flows of this element.
      potentialLoop.addLast(element);
      Either<List<ExecutableFlowNode>, ?> isPartOfLoop = Either.right(null);
      for (final ExecutableSequenceFlow outgoing : element.getOutgoing()) {
        isPartOfLoop = checkForStraightThroughProcessingLoop(potentialLoop, outgoing.getTarget());
        if (isPartOfLoop.isLeft()) {
          break;
        }
      }

      if (isPartOfLoop.isRight()) {
        potentialLoop.remove(element);
      }
      return isPartOfLoop;

    } else {
      // No loops have been found. We can return an Either.Right to indicate success.
      return Either.right(null);
    }
  }

  /**
   * This method will decide if we are in a loop or not. It does so by checking if the potentialLoop
   * list contains the element we are currently checking. It also has an extra check to make sure we
   * don't detect loops between just gateways. As of the time of writing it is unclear whether we
   * should reject these or not.
   *
   * @param potentialLoop the list of elements in a potential loop
   * @param element the element we are currently checking
   * @return a boolean indicating if we are in a loop or not
   */
  private static boolean foundLoop(
      final LinkedList<ExecutableFlowNode> potentialLoop, final ExecutableFlowNode element) {
    final var isLoopOfOnlyWhitelistedElements =
        potentialLoop.stream()
            .map(AbstractFlowElement::getElementType)
            .allMatch(WHITELISTED_ELEMENT_TYPES::contains);
    return potentialLoop.contains(element) && !isLoopOfOnlyWhitelistedElements;
  }

  /**
   * Create a descriptive failure message which will be part of the rejection message. It mentions
   * which resource and process contain the issue. It also lists the chain of element ids that are
   * looping.
   *
   * @param resource the resource file that we've validated for loops
   * @param executableProcess the process that we've validated for loops
   * @param loopingElements a list of looping elements. The first and last element of this list will
   *     be the same.
   * @return a descriptive failure message
   */
  private static String createFailureMessage(
      final DeploymentResource resource,
      final ExecutableProcess executableProcess,
      final List<ExecutableFlowNode> loopingElements) {
    final List<String> loopingElementIds =
        loopingElements.stream()
            .map(AbstractFlowElement::getId)
            .map(BufferUtil::bufferAsString)
            .toList();

    final StringWriter writer = new StringWriter();
    writer.write(
        String.format(
            "`%s`: - Process: %s",
            resource.getResourceName(), BufferUtil.bufferAsString(executableProcess.getId())));
    writer.write("\n");
    writer.write(
        String.format(
            "    - ERROR: Processes are not allowed to contain a straight-through processing loop: %s",
            String.join(" > ", loopingElementIds)));
    writer.write("\n");
    return writer.toString();
  }
}
