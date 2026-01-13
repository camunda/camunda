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

import io.camunda.client.CamundaClient;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.ImmutableIncreaseTimeInstruction;
import io.camunda.process.test.api.dsl.instructions.IncreaseTimeInstruction;
import io.camunda.process.test.impl.dsl.instructions.IncreaseTimeInstructionHandler;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IncreaseTimeInstructionTest {

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final IncreaseTimeInstructionHandler instructionHandler =
      new IncreaseTimeInstructionHandler();

  @Test
  void shouldIncreaseTimeWithDuration() {
    // given
    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration("P2D").build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(Duration.ofDays(2));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldIncreaseTimeWithHourDuration() {
    // given
    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration("PT1H").build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(Duration.ofHours(1));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }

  @Test
  void shouldIncreaseTimeWithMinuteDuration() {
    // given
    final IncreaseTimeInstruction instruction =
        ImmutableIncreaseTimeInstruction.builder().duration("PT30M").build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).increaseTime(Duration.ofMinutes(30));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
