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
 * The business logic of a BPMN element.
 *
 * <p>The execution of an element is divided into multiple steps that represents the lifecycle of
 * the element. Each step defines a set of actions that can be performed in this step. The
 * transition to the next step must be triggered explicitly in the current step.
 *
 * @param <T> the type that represents the BPMN element
 */
public interface BpmnElementProcessor<T extends ExecutableFlowElement> {

  /** @return the class that represents the BPMN element */
  Class<T> getType();

  /**
   * The element is about to be entered. Perform every action to initialize and activate the
   * element.
   *
   * <p>If the element is a wait-state (i.e. it is waiting for an event or an external trigger) then
   * it is waiting after this step to continue. Otherwise, it continues directly to the next step.
   *
   * <p>Possible actions:
   *
   * <ul>
   *   <li>apply input mappings
   *   <li>open event subscriptions
   *   <li>initialize child elements - if the element is a container (e.g. a sub-process)
   * </ul>
   *
   * Next step:
   *
   * <ul>
   *   <li>activating - the element is initialized
   *   <li>activated - if no incidents raised
   *   <li>complete - if no incidents raised & not a wait-state.
   * </ul>
   *
   * @param element the instance of the BPMN element that is executed
   * @param context process instance-related data of the element that is executed
   */
  default void onActivate(final T element, final BpmnElementContext context) {}

  /**
   * The element is going to be left. Perform every action to leave the element and continue with
   * the next element.
   *
   * <p>Possible actions:
   *
   * <ul>
   *   <li>apply output mappings
   *   <li>close event subscriptions
   *   <li>take outgoing sequence flows - if any
   *   <li>continue with parent element - if no outgoing sequence flows
   *   <li>clean up the state
   * </ul>
   *
   * Next step: none.
   *
   * @param element the instance of the BPMN element that is executed
   * @param context process instance-related data of the element that is executed
   */
  default void onComplete(final T element, final BpmnElementContext context) {}

  /**
   * The element is going to be terminated. Perform every action to terminate the element and
   * continue with the element that caused the termination (e.g. the triggered boundary event).
   *
   * <p>Possible actions:
   *
   * <ul>
   *   <li>close event subscriptions
   *   <li>resolve incidents
   *   <li>activate the triggered boundary event - if any
   *   <li>activate the triggered event sub-process - if any
   *   <li>continue with parent element
   *   <li>clean up the state
   * </ul>
   *
   * Next step: none.
   *
   * @param element the instance of the BPMN element that is executed
   * @param context process instance-related data of the element that is executed
   */
  default void onTerminate(final T element, final BpmnElementContext context) {}
}
