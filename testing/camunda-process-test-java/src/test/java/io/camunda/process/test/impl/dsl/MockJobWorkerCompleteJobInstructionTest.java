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
import io.camunda.process.test.api.dsl.instructions.MockJobWorkerCompleteJobInstruction;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder;
import io.camunda.process.test.api.mock.JobWorkerMockBuilder.JobWorkerMock;
import io.camunda.process.test.impl.dsl.instructions.MockJobWorkerCompleteJobInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class MockJobWorkerCompleteJobInstructionTest {

  private static final String JOB_TYPE = "send-email";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  @Mock private JobWorkerMockBuilder jobWorkerMockBuilder;

  @Mock private JobWorkerMock jobWorkerMock;

  private final MockJobWorkerCompleteJobInstructionHandler instructionHandler =
      new MockJobWorkerCompleteJobInstructionHandler();

  @Test
  void shouldCompleteJobWithoutVariables() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        new TestMockJobWorkerCompleteJobInstruction(JOB_TYPE, Collections.emptyMap(), false);

    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
    when(jobWorkerMockBuilder.thenComplete()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete();

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");
    variables.put("timestamp", 123456789L);

    final MockJobWorkerCompleteJobInstruction instruction =
        new TestMockJobWorkerCompleteJobInstruction(JOB_TYPE, variables, false);

    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
    when(jobWorkerMockBuilder.thenComplete(variables)).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenComplete(variables);

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldCompleteJobWithExampleData() {
    // given
    final MockJobWorkerCompleteJobInstruction instruction =
        new TestMockJobWorkerCompleteJobInstruction(JOB_TYPE, Collections.emptyMap(), true);

    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  @Test
  void shouldIgnoreVariablesWhenWithExampleDataIsTrue() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("status", "sent");

    final MockJobWorkerCompleteJobInstruction instruction =
        new TestMockJobWorkerCompleteJobInstruction(JOB_TYPE, variables, true);

    when(processTestContext.mockJobWorker(JOB_TYPE)).thenReturn(jobWorkerMockBuilder);
    when(jobWorkerMockBuilder.thenCompleteWithExampleData()).thenReturn(jobWorkerMock);

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).mockJobWorker(JOB_TYPE);
    verify(jobWorkerMockBuilder).thenCompleteWithExampleData();

    verifyNoMoreInteractions(processTestContext, camundaClient, assertionFacade, jobWorkerMockBuilder);
  }

  // Test implementation for MockJobWorkerCompleteJobInstruction
  private static class TestMockJobWorkerCompleteJobInstruction
      implements MockJobWorkerCompleteJobInstruction {
    private final String jobType;
    private final Map<String, Object> variables;
    private final boolean withExampleData;

    TestMockJobWorkerCompleteJobInstruction(
        final String jobType, final Map<String, Object> variables, final boolean withExampleData) {
      this.jobType = jobType;
      this.variables = variables;
      this.withExampleData = withExampleData;
    }

    @Override
    public String getType() {
      return "MOCK_JOB_WORKER_COMPLETE_JOB";
    }

    @Override
    public String getJobType() {
      return jobType;
    }

    @Override
    public Map<String, Object> getVariables() {
      return variables;
    }

    @Override
    public boolean getWithExampleData() {
      return withExampleData;
    }
  }
}
