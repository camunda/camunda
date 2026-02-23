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
import io.camunda.client.api.command.CorrelateMessageCommandStep1.CorrelateMessageCommandStep3;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.CorrelateMessageInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableCorrelateMessageInstruction;
import io.camunda.process.test.impl.dsl.instructions.CorrelateMessageInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CorrelateMessageInstructionTest {

  private static final String MESSAGE_NAME = "message1";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final CorrelateMessageInstructionHandler instructionHandler =
      new CorrelateMessageInstructionHandler();

  @Test
  void shouldCorrelateMessageWithoutCorrelationKey() {
    // given
    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder().name(MESSAGE_NAME).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .withoutCorrelationKey();

    verify(mockCommand).variables(Collections.emptyMap());
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldCorrelateMessageWithCorrelationKey() {
    // given
    final String correlationKey = "order-12345";
    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder()
            .name(MESSAGE_NAME)
            .correlationKey(correlationKey)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .correlationKey(correlationKey);

    verify(mockCommand).variables(Collections.emptyMap());
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("orderId", 12345);
    variables.put("status", "approved");

    final CorrelateMessageInstruction instruction =
        ImmutableCorrelateMessageInstruction.builder()
            .name(MESSAGE_NAME)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newCorrelateMessageCommand();

    final CorrelateMessageCommandStep3 mockCommand =
        camundaClient
            .newCorrelateMessageCommand()
            .messageName(MESSAGE_NAME)
            .withoutCorrelationKey();

    verify(mockCommand).variables(variables);
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }
}
