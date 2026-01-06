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
import io.camunda.client.api.command.BroadcastSignalCommandStep1.BroadcastSignalCommandStep2;
import io.camunda.process.test.api.CamundaProcessTestContext;
import io.camunda.process.test.api.dsl.instructions.BroadcastSignalInstruction;
import io.camunda.process.test.api.dsl.instructions.ImmutableBroadcastSignalInstruction;
import io.camunda.process.test.impl.dsl.instructions.BroadcastSignalInstructionHandler;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class BroadcastSignalInstructionTest {

  private static final String SIGNAL_NAME = "signal1";

  @Mock private CamundaProcessTestContext processTestContext;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  private CamundaClient camundaClient;

  @Mock private AssertionFacade assertionFacade;

  private final BroadcastSignalInstructionHandler instructionHandler =
      new BroadcastSignalInstructionHandler();

  @Test
  void shouldBroadcastSignalWithSignalName() {
    // given
    final BroadcastSignalInstruction instruction =
        ImmutableBroadcastSignalInstruction.builder().signalName(SIGNAL_NAME).build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newBroadcastSignalCommand();

    final BroadcastSignalCommandStep2 mockCommand =
        camundaClient.newBroadcastSignalCommand().signalName(SIGNAL_NAME);

    verify(mockCommand).variables(Collections.emptyMap());
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }

  @Test
  void shouldSetVariables() {
    // given
    final Map<String, Object> variables = new HashMap<>();
    variables.put("key1", "value1");
    variables.put("key2", 123);

    final BroadcastSignalInstruction instruction =
        ImmutableBroadcastSignalInstruction.builder()
            .signalName(SIGNAL_NAME)
            .putAllVariables(variables)
            .build();

    // when
    instructionHandler.execute(instruction, processTestContext, camundaClient, assertionFacade);

    // then
    verify(camundaClient).newBroadcastSignalCommand();

    final BroadcastSignalCommandStep2 mockCommand =
        camundaClient.newBroadcastSignalCommand().signalName(SIGNAL_NAME);

    verify(mockCommand).variables(variables);
    verify(mockCommand).send();

    verifyNoMoreInteractions(camundaClient, processTestContext, mockCommand, assertionFacade);
  }
}
