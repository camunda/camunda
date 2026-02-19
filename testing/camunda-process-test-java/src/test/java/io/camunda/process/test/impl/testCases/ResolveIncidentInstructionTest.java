/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.process.test.impl.testCases;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.testCases.ImmutableIncidentSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableResolveIncidentInstruction;
import io.camunda.process.test.api.testCases.instructions.ResolveIncidentInstruction;
import io.camunda.process.test.impl.testCases.instructions.ResolveIncidentInstructionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class ResolveIncidentInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private IncidentFilter incidentFilter;

  @Captor private ArgumentCaptor<IncidentSelector> selectorCaptor;

  private final ResolveIncidentInstructionHandler instructionHandler =
      new ResolveIncidentInstructionHandler();

  @Test
  void shouldResolveIncidentByElementId() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(ImmutableIncidentSelector.builder().elementId("task1").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).elementId("task1");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldResolveIncidentByProcessDefinitionId() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder().processDefinitionId("my-process").build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).processDefinitionId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @Test
  void shouldResolveIncidentWithCombinedSelector() {
    // given
    final ResolveIncidentInstruction instruction =
        ImmutableResolveIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder()
                    .processDefinitionId("my-process")
                    .elementId("task1")
                    .build())
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).resolveIncident(selectorCaptor.capture());

    selectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).elementId("task1");
    verify(incidentFilter).processDefinitionId("my-process");

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }
}
