/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.processtest.actions;

import io.camunda.service.processtest.ProcessTestActions;
import io.camunda.service.processtest.TestContext;
import io.camunda.service.processtest.dsl.TestAction;

public record CreateProcessInstanceAction(
    String processId, String variables, String processInstanceAlias) implements TestAction {

  @Override
  public void execute(final TestContext context, final ProcessTestActions actions) {
    final long processInstanceKey = actions.createProcessInstance(processId, variables);

    context.addProcessInstance(processInstanceAlias, processInstanceKey);
  }
}
