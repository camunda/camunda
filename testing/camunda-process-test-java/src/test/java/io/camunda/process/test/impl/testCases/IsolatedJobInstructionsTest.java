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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.instructions.ImmutableReleaseJobInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableRunCalledProcessInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableStubCallActivityCompleteInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableStubCallActivityThrowErrorInstruction;
import io.camunda.process.test.impl.testCases.instructions.ReleaseJobInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.RunCalledProcessInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.StubCallActivityCompleteInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.StubCallActivityThrowErrorInstructionHandler;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsolatedJobInstructionsTest {

  private static final String ELEMENT_ID = "ship-order";
  private static final String STUB_JOB_TYPE = "io.camunda.zeebe:callActivityStub";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;

  private final io.camunda.process.test.api.testCases.JobSelector byElementId =
      ImmutableJobSelector.builder().elementId(ELEMENT_ID).build();

  @Test
  void shouldReleaseJob() {
    // when
    new ReleaseJobInstructionHandler()
        .execute(
            ImmutableReleaseJobInstruction.builder().jobSelector(byElementId).build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    verify(processTestContext).releaseJob(jobSelectorCaptor.capture());
    assertSelectsElement();
  }

  @Test
  void shouldRunCalledProcess() {
    // when
    new RunCalledProcessInstructionHandler()
        .execute(
            ImmutableRunCalledProcessInstruction.builder()
                .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    verify(processTestContext).runCalledProcess(jobSelectorCaptor.capture());
    assertSelectsStubJobOfElement();
  }

  @Test
  void shouldStandInForCallActivityByCompletingItsStubJob() {
    // when
    new StubCallActivityCompleteInstructionHandler()
        .execute(
            ImmutableStubCallActivityCompleteInstruction.builder()
                .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
                .putVariables("trackingCode", "TRK-1")
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    verify(processTestContext)
        .completeJob(
            jobSelectorCaptor.capture(), eq(Collections.singletonMap("trackingCode", "TRK-1")));
    assertSelectsStubJobOfElement();
  }

  @Test
  void shouldThrowBpmnErrorFromCallActivityStubJob() {
    // when
    new StubCallActivityThrowErrorInstructionHandler()
        .execute(
            ImmutableStubCallActivityThrowErrorInstruction.builder()
                .elementSelector(ImmutableElementSelector.builder().elementId(ELEMENT_ID).build())
                .errorCode("SHIPPING_DECLINED")
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    verify(processTestContext)
        .throwBpmnErrorFromJob(
            jobSelectorCaptor.capture(), eq("SHIPPING_DECLINED"), eq(Collections.emptyMap()));
    assertSelectsStubJobOfElement();
  }

  private void assertSelectsElement() {
    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).elementId(ELEMENT_ID);
  }

  private void assertSelectsStubJobOfElement() {
    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).type(STUB_JOB_TYPE);
    verify(jobFilter).elementId(ELEMENT_ID);
  }
}
