/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import io.camunda.process.test.api.assertions.DecisionInstanceAssert;
import io.camunda.process.test.api.assertions.DecisionSelector;
import io.camunda.process.test.api.assertions.ProcessInstanceAssert;
import io.camunda.process.test.api.assertions.ProcessInstanceSelector;
import io.camunda.process.test.api.assertions.UserTaskAssert;
import io.camunda.process.test.api.assertions.UserTaskSelector;

/** Facade to provide assertions for different entities. */
public interface AssertionFacade {

  /**
   * Returns the assertion object for the given process instance selector.
   *
   * @param processInstanceSelector the selector to identify the process instance
   * @return the assertion object
   */
  ProcessInstanceAssert assertThatProcessInstance(
      final ProcessInstanceSelector processInstanceSelector);

  /**
   * Returns the assertion object for the given user task selector.
   *
   * @param userTaskSelector the selector to identify the user task
   * @return the assertion object
   */
  UserTaskAssert assertThatUserTask(final UserTaskSelector userTaskSelector);

  /**
   * Returns the assertion object for the given decision selector.
   *
   * @param decisionSelector the selector to identify the decision
   * @return the assertion object
   */
  DecisionInstanceAssert assertThatDecision(final DecisionSelector decisionSelector);
}
