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
package io.camunda.process.test.impl.dsl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.ImmutableMockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.api.dsl.instructions.MockJobWorkerThrowBpmnErrorInstruction;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.impl.dsl.instructions.MockJobWorkerThrowBpmnErrorInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerThrowBpmnErrorInstructionTest {

  private static final String JOB_TYPE = "validate-order";
  private static final String ERROR_CODE = "INVALID_ORDER";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private JobWorkerMockBuilder jobWorkerMockBuilder;

  private final MockJobWorkerThrowBpmnErrorInstructionHandler instructionHandler =
      new MockJobWorkerThrowBpmnErrorInstructionHandler();

  @BeforeEach
  void setUp() {
    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithoutVariablesOrErrorMessage() {
    // given
    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("reason", "Missing required field");
    variables.put("field", "email");

    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessage() {
    // given
    final String errorMessage = "Order validation failed";
    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .errorMessage(errorMessage)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder)
        .thenThrowBpmnError(ERROR_CODE, errorMessage, Collections.emptyMap());

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldThrowBpmnErrorWithErrorMessageAndVariables() {
    // given
    final String errorMessage = "Order validation failed";
    final Map<String, Object> variables = new HashMap<>();
    variables.put("reason", "Missing required field");
    variables.put("field", "email");

    final MockJobWorkerThrowBpmnErrorInstruction instruction =
        ImmutableMockJobWorkerThrowBpmnErrorInstruction.builder()
            .jobType(JOB_TYPE)
            .errorCode(ERROR_CODE)
            .errorMessage(errorMessage)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenThrowBpmnError(ERROR_CODE, errorMessage, variables);

    verifyNoMoreInteractions(
        processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }
}
