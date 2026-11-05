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
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import io.camunda.process.test.api.testCases.TestCaseJobReservationToken;
import io.camunda.process.test.api.testCases.instructions.ImmutableCompleteJobInstruction;
import io.camunda.process.test.api.testCases.instructions.ImmutableThrowBpmnErrorFromJobInstruction;
import io.camunda.process.test.impl.testCases.instructions.CompleteJobInstructionHandler;
import io.camunda.process.test.impl.testCases.instructions.ThrowBpmnErrorFromJobInstructionHandler;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IsolatedJobInstructionsTest {

  private static final String ELEMENT_ID = "ship-order";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;
  @Mock private JobFilter jobFilter;
  @Captor private ArgumentCaptor<JobSelector> jobSelectorCaptor;

  private final io.camunda.process.test.api.testCases.JobSelector byElementId =
      ImmutableJobSelector.builder().elementId(ELEMENT_ID).build();

  private final io.camunda.process.test.api.testCases.JobSelector stubJobOfElement =
      ImmutableJobSelector.builder().jobKind("CALL_ACTIVITY_STUB").elementId(ELEMENT_ID).build();

  @Test
  void shouldSendTheRunsTokenForThePlaceholder() {
    // given
    final TestRunContext runContext = TestRunContext.disabled();

    // when
    new CompleteJobInstructionHandler(runContext)
        .execute(
            ImmutableCompleteJobInstruction.builder()
                .jobSelector(byElementId)
                .jobReservationToken(TestCaseJobReservationToken.PLACEHOLDER)
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then the saved placeholder never reaches the cluster: the run's token is set for the command
    // and cleared right after it, so it cannot leak to the next instruction
    final String runToken =
        runContext.resolveJobReservationToken(TestCaseJobReservationToken.PLACEHOLDER);
    assertThat(runToken).isNotEqualTo(TestCaseJobReservationToken.PLACEHOLDER);
    final InOrder inOrder = inOrder(processTestContext);
    inOrder.verify(processTestContext).setJobReservationToken(runToken);
    inOrder.verify(processTestContext).completeJob(any(JobSelector.class), anyMap());
    inOrder.verify(processTestContext).setJobReservationToken(null);
  }

  @Test
  void shouldSendTheSameTokenFromCompleteAndThrowInOneRun() {
    // given
    final TestRunContext runContext = TestRunContext.disabled();

    // when
    new CompleteJobInstructionHandler(runContext)
        .execute(
            ImmutableCompleteJobInstruction.builder()
                .jobSelector(byElementId)
                .jobReservationToken(TestCaseJobReservationToken.PLACEHOLDER)
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);
    new ThrowBpmnErrorFromJobInstructionHandler(runContext)
        .execute(
            ImmutableThrowBpmnErrorFromJobInstruction.builder()
                .jobSelector(byElementId)
                .errorCode("ERROR")
                .jobReservationToken(TestCaseJobReservationToken.PLACEHOLDER)
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    final String runToken =
        runContext.resolveJobReservationToken(TestCaseJobReservationToken.PLACEHOLDER);
    verify(processTestContext, times(2)).setJobReservationToken(runToken);
    verify(processTestContext, times(2)).setJobReservationToken(null);
  }

  @Test
  void shouldSendNoTokenWhenTheInstructionHasNone() {
    // when
    new CompleteJobInstructionHandler(TestRunContext.disabled())
        .execute(
            ImmutableCompleteJobInstruction.builder().jobSelector(byElementId).build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    verify(processTestContext, never()).setJobReservationToken(any());
  }

  @Test
  void shouldSendALiteralTokenAsWritten() {
    // when
    new CompleteJobInstructionHandler(TestRunContext.disabled())
        .execute(
            ImmutableCompleteJobInstruction.builder()
                .jobSelector(byElementId)
                .jobReservationToken("literal-token")
                .build(),
            processTestContext,
            camundaClient,
            assertionFacade);

    // then
    final InOrder inOrder = inOrder(processTestContext);
    inOrder.verify(processTestContext).setJobReservationToken("literal-token");
    inOrder.verify(processTestContext).setJobReservationToken(null);
  }

  @Test
  void shouldStandInForCallActivityByCompletingItsStubJob() {
    // when
    new CompleteJobInstructionHandler(TestRunContext.disabled())
        .execute(
            ImmutableCompleteJobInstruction.builder()
                .jobSelector(stubJobOfElement)
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
    new ThrowBpmnErrorFromJobInstructionHandler(TestRunContext.disabled())
        .execute(
            ImmutableThrowBpmnErrorFromJobInstruction.builder()
                .jobSelector(stubJobOfElement)
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

  private void assertSelectsStubJobOfElement() {
    jobSelectorCaptor.getValue().applyFilter(jobFilter);
    verify(jobFilter).kind(JobKind.CALL_ACTIVITY_STUB);
    verify(jobFilter).elementId(ELEMENT_ID);
  }
}
