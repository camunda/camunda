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
package io.camunda.process.test.impl.testCases.instructions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.enums.JobKind;
import io.camunda.client.api.search.filter.JobFilter;
import io.camunda.process.test.api.assertions.JobSelector;
import io.camunda.process.test.api.testCases.ImmutableElementSelector;
import io.camunda.process.test.api.testCases.ImmutableJobSelector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class InstructionSelectorFactoryTest {

  private static final String PROCESS_DEFINITION_ID = "process";
  private static final String JOB_TYPE = "job-type";

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private JobFilter jobFilter;

  @Test
  void shouldBuildJobSelectorWithoutInstanceLookup() {
    // given
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder()
            .jobType(JOB_TYPE)
            .processDefinitionId(PROCESS_DEFINITION_ID)
            .build();

    // when
    final JobSelector selector = InstructionSelectorFactory.buildJobSelector(dsl);
    selector.applyFilter(jobFilter);

    // then
    verifyNoInteractions(camundaClient);
    verify(jobFilter, never()).processInstanceKey(anyLong());
    verify(jobFilter).type(JOB_TYPE);
  }

  @Test
  void shouldBuildJobKindSelector() {
    // given
    final io.camunda.process.test.api.testCases.JobSelector dsl =
        ImmutableJobSelector.builder().jobKind("CALL_ACTIVITY_STUB").build();

    // when
    final JobSelector selector = InstructionSelectorFactory.buildJobSelector(dsl);
    selector.applyFilter(jobFilter);

    // then
    verify(jobFilter).kind(JobKind.CALL_ACTIVITY_STUB);
  }

  @Test
  void shouldSelectTimerByElementId() {
    // given
    final io.camunda.process.test.api.testCases.ElementSelector selector =
        ImmutableElementSelector.builder().elementId("timer").build();

    // when
    final String elementId = InstructionSelectorFactory.buildTimerElementId(selector);

    // then
    assertThat(elementId).isEqualTo("timer");
  }
}
