/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElement;

/**
 * The business logic of an BPMN element container (e.g. a sub-process).
 *
 * <p>The execution of an element is divided into multiple steps that represents the lifecycle of
 * the element. Each step defines a set of actions that can be performed in this step. The
 * transition to the next step must be triggered explicitly in the current step.
 *
 * @param <T> the type that represents the BPMN element
 */
public interface BpmnElementContainerProcessor<T extends ExecutableFlowElement>
    extends BpmnElementProcessor<T> {

  /**
   * A child element is completed. Leave the element container if it has no more active child
   * elements.
   *
   * @param element the instance of the BPMN element container
   * @param flowScopeContext process instance-related data of the element container
   * @param childContext process instance-related data of the child element that is completed
   */
  void onChildCompleted(
      final T element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext);

  /**
   * A child element is terminated. Terminate the element container if it has no more active child
   * elements. Or, continue with the interrupting event sub-process that was triggered and caused
   * the termination.
   *
   * @param element the instance of the BPMN element container
   * @param flowScopeContext process instance-related data of the element container
   * @param childContext process instance-related data of the child element that is terminated
   */
  void onChildTerminated(
      T element, BpmnElementContext flowScopeContext, BpmnElementContext childContext);
}
