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
import io.camunda.process.test.api.dsl.instructions.ImmutableSetTimeInstruction;
import io.camunda.process.test.api.dsl.instructions.SetTimeInstruction;
import io.camunda.process.test.impl.dsl.instructions.SetTimeInstructionHandler;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SetTimeInstructionTest {

  private static final String TIME_STRING = "2025-12-01T10:00:00Z";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final SetTimeInstructionHandler instructionHandler = new SetTimeInstructionHandler();

  @Test
  void shouldSetTime() {
    // given
    final SetTimeInstruction instruction =
        ImmutableSetTimeInstruction.builder().time(TIME_STRING).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(processTestContext).setTime(Instant.parse(TIME_STRING));

    verifyNoMoreInteractions(camundaClient, processTestContext, assertionFacade);
  }
}
