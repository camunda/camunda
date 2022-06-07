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
package io.camunda.zeebe.client.impl.command;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.CreateProcessInstanceRequest;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.ProcessInstanceCreationStartInstruction;
import java.util.List;
import org.junit.Test;

public class CreateProcessInstanceCommandImplTest {

  public static final String ELEMENT_ID_A = "elementId_A";
  public static final String ELEMENT_ID_B = "elementId_B";

  @Test
  public void shouldAddStartInstruction() {
    // given
    final CreateProcessInstanceCommandImpl sut =
        new CreateProcessInstanceCommandImpl(null, null, null, null);

    // when
    sut.startBeforeElement(ELEMENT_ID_A);
    final CreateProcessInstanceRequest request = sut.buildRequest();

    // then
    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructionsList();
    assertThat(startInstructionList).hasSize(1);
    final ProcessInstanceCreationStartInstruction startInstruction = startInstructionList.get(0);
    assertThat(startInstruction.getElementId()).isEqualTo(ELEMENT_ID_A);
  }

  @Test
  public void shouldAddMultipleStartInstructions() {
    // given
    final CreateProcessInstanceCommandImpl sut =
        new CreateProcessInstanceCommandImpl(null, null, null, null);

    // when
    sut.startBeforeElement(ELEMENT_ID_A);
    sut.startBeforeElement(ELEMENT_ID_B);
    final CreateProcessInstanceRequest request = sut.buildRequest();

    // then
    final List<ProcessInstanceCreationStartInstruction> startInstructionList =
        request.getStartInstructionsList();
    assertThat(startInstructionList).hasSize(2);
    final ProcessInstanceCreationStartInstruction startInstructionA = startInstructionList.get(0);
    assertThat(startInstructionA.getElementId()).isEqualTo(ELEMENT_ID_A);
    final ProcessInstanceCreationStartInstruction startInstructionB = startInstructionList.get(1);
    assertThat(startInstructionB.getElementId()).isEqualTo(ELEMENT_ID_B);
  }
}
