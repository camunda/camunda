/*
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.process.test.impl.testCases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.IncidentFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.IncidentAssert;
import io.camunda.process.test.api.assertions.IncidentSelector;
import io.camunda.process.test.api.testCases.ImmutableIncidentSelector;
import io.camunda.process.test.api.testCases.instructions.AssertIncidentInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableAssertIncidentInstruction;
import io.camunda.process.test.api.testCases.instructions.assertIncident.IncidentErrorType;
import io.camunda.process.test.api.testCases.instructions.assertIncident.IncidentState;
import io.camunda.process.test.impl.testCases.instructions.AssertIncidentInstructionHandler;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class AssertIncidentInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;
  @Mock private CamundaClient camundaClient;
  @Mock private AssertionFacade assertionFacade;
  @Mock private IncidentAssert incidentAssert;
  @Mock private IncidentFilter incidentFilter;
  @Captor private ArgumentCaptor<IncidentSelector> incidentSelectorCaptor;

  private final AssertIncidentInstructionHandler instructionHandler =
      new AssertIncidentInstructionHandler();

  @Test
  void shouldSelectIncidentByAllSelectorProperties() {
    // given
    final AssertIncidentInstruction instruction =
        ImmutableAssertIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder()
                    .elementId("payment-task")
                    .processDefinitionId("payment-process")
                    .build())
            .build();

    // when
    execute(instruction);

    // then
    verify(assertionFacade).assertThatIncident(incidentSelectorCaptor.capture());
    incidentSelectorCaptor.getValue().applyFilter(incidentFilter);
    verify(incidentFilter).elementId("payment-task");
    verify(incidentFilter).processDefinitionId("payment-process");
    verifyNoInteractions(incidentAssert);
    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade);
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("stateAssertions")
  void shouldAssertState(
      final IncidentState state, final Consumer<IncidentAssert> expectedAssertion) {
    // given
    final AssertIncidentInstruction instruction =
        ImmutableAssertIncidentInstruction.builder()
            .incidentSelector(ImmutableIncidentSelector.builder().elementId("payment-task").build())
            .state(state)
            .build();

    // when
    execute(instruction);

    // then
    verify(assertionFacade).assertThatIncident(any());
    expectedAssertion.accept(verify(incidentAssert));
    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, incidentAssert);
  }

  @ParameterizedTest
  @EnumSource(IncidentErrorType.class)
  void shouldAssertEveryErrorType(final IncidentErrorType errorType) {
    // given
    final AssertIncidentInstruction instruction =
        ImmutableAssertIncidentInstruction.builder()
            .incidentSelector(ImmutableIncidentSelector.builder().elementId("payment-task").build())
            .errorType(errorType)
            .build();

    // when
    execute(instruction);

    // then
    verify(assertionFacade).assertThatIncident(any());
    verify(incidentAssert)
        .hasErrorType(
            io.camunda.client.api.search.enums.IncidentErrorType.valueOf(errorType.name()));
    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, incidentAssert);
  }

  @Test
  void shouldKeepJsonAndClientErrorTypesInSync() {
    // given
    final Set<String> jsonErrorTypes =
        Arrays.stream(IncidentErrorType.values()).map(Enum::name).collect(Collectors.toSet());
    final Set<String> clientErrorTypes =
        Arrays.stream(io.camunda.client.api.search.enums.IncidentErrorType.values())
            .map(Enum::name)
            .collect(Collectors.toSet());

    // when / then
    assertThat(jsonErrorTypes).isEqualTo(clientErrorTypes);
  }

  @Test
  void shouldAssertErrorMessage() {
    // given
    final AssertIncidentInstruction instruction =
        ImmutableAssertIncidentInstruction.builder()
            .incidentSelector(ImmutableIncidentSelector.builder().elementId("payment-task").build())
            .errorMessage("Payment worker failed")
            .build();

    // when
    execute(instruction);

    // then
    verify(assertionFacade).assertThatIncident(any());
    verify(incidentAssert).hasErrorMessage("Payment worker failed");
    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, incidentAssert);
  }

  @Test
  void shouldAssertElementId() {
    // given
    final AssertIncidentInstruction instruction =
        ImmutableAssertIncidentInstruction.builder()
            .incidentSelector(
                ImmutableIncidentSelector.builder().processDefinitionId("payment-process").build())
            .elementId("payment-task")
            .build();

    // when
    execute(instruction);

    // then
    verify(assertionFacade).assertThatIncident(any());
    verify(incidentAssert).hasElementId("payment-task");
    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, incidentAssert);
  }

  static Stream<Arguments> stateAssertions() {
    return Stream.of(
        Arguments.of(IncidentState.IS_ACTIVE, (Consumer<IncidentAssert>) IncidentAssert::isActive),
        Arguments.of(
            IncidentState.IS_RESOLVED, (Consumer<IncidentAssert>) IncidentAssert::isResolved));
  }

  private void execute(final AssertIncidentInstruction instruction) {
    when(assertionFacade.assertThatIncident(any())).thenReturn(incidentAssert);
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);
  }
}
