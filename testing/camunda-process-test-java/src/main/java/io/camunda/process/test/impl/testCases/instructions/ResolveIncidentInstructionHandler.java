/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases.instructions;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.testCases.instructions.ResolveIncidentInstruction;
import io.camunda.process.test.impl.testCases.AssertionFacade;
import io.camunda.process.test.impl.testCases.TestCaseInstructionHandler;

public class ResolveIncidentInstructionHandler
    implements TestCaseInstructionHandler<ResolveIncidentInstruction> {

  @Override
  public void execute(
      final ResolveIncidentInstruction instruction,
      final CamundaProcessTestContext context,
      final CamundaClient camundaClient,
      final AssertionFacade assertionFacade) {

    final IncidentSelector incidentSelector =
        InstructionSelectorFactory.buildIncidentSelector(instruction.getIncidentSelector());

    context.resolveIncident(incidentSelector);
  }

  @Override
  public Class<ResolveIncidentInstruction> getInstructionType() {
    return ResolveIncidentInstruction.class;
  }
}
