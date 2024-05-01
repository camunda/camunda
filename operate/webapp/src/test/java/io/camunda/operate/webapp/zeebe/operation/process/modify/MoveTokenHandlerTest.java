/*
 * Copyright Camunda Services GmbH
 *
 * BY INSTALLING, DOWNLOADING, ACCESSING, USING, OR DISTRIBUTING THE SOFTWARE (“USE”), YOU INDICATE YOUR ACCEPTANCE TO AND ARE ENTERING INTO A CONTRACT WITH, THE LICENSOR ON THE TERMS SET OUT IN THIS AGREEMENT. IF YOU DO NOT AGREE TO THESE TERMS, YOU MUST NOT USE THE SOFTWARE. IF YOU ARE RECEIVING THE SOFTWARE ON BEHALF OF A LEGAL ENTITY, YOU REPRESENT AND WARRANT THAT YOU HAVE THE ACTUAL AUTHORITY TO AGREE TO THE TERMS AND CONDITIONS OF THIS AGREEMENT ON BEHALF OF SUCH ENTITY.
 * “Licensee” means you, an individual, or the entity on whose behalf you receive the Software.
 *
 * Permission is hereby granted, free of charge, to the Licensee obtaining a copy of this Software and associated documentation files to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject in each case to the following conditions:
 * Condition 1: If the Licensee distributes the Software or any derivative works of the Software, the Licensee must attach this Agreement.
 * Condition 2: Without limiting other conditions in this Agreement, the grant of rights is solely for non-production use as defined below.
 * "Non-production use" means any use of the Software that is not directly related to creating products, services, or systems that generate revenue or other direct or indirect economic benefits.  Examples of permitted non-production use include personal use, educational use, research, and development. Examples of prohibited production use include, without limitation, use for commercial, for-profit, or publicly accessible systems or use for commercial or revenue-generating purposes.
 *
 * If the Licensee is in breach of the Conditions, this Agreement, including the rights granted under it, will automatically terminate with immediate effect.
 *
 * SUBJECT AS SET OUT BELOW, THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * NOTHING IN THIS AGREEMENT EXCLUDES OR RESTRICTS A PARTY’S LIABILITY FOR (A) DEATH OR PERSONAL INJURY CAUSED BY THAT PARTY’S NEGLIGENCE, (B) FRAUD, OR (C) ANY OTHER LIABILITY TO THE EXTENT THAT IT CANNOT BE LAWFULLY EXCLUDED OR RESTRICTED.
 */
package io.camunda.operate.webapp.zeebe.operation.process.modify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import io.camunda.operate.entities.FlowNodeState;
import io.camunda.operate.webapp.reader.FlowNodeInstanceReader;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification;
import io.camunda.operate.webapp.rest.dto.operation.ModifyProcessInstanceRequestDto.Modification.Type;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2;
import io.camunda.zeebe.client.api.command.ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep3;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class MoveTokenHandlerTest {
  @Mock private FlowNodeInstanceReader mockFlowNodeInstanceReader;

  private MoveTokenHandler moveTokenHandler;

  private ModifyProcessInstanceCommandStep1 mockZeebeCommand;

  @BeforeEach
  public void setup() {
    moveTokenHandler = new MoveTokenHandler(mockFlowNodeInstanceReader);

    mockZeebeCommand =
        Mockito.mock(
            ModifyProcessInstanceCommandStep1.class,
            withSettings()
                .extraInterfaces(
                    ModifyProcessInstanceCommandStep2.class,
                    ModifyProcessInstanceCommandStep3.class));

    when(mockZeebeCommand.activateElement(anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(mockZeebeCommand.activateElement(anyString(), anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(mockZeebeCommand.terminateElement(anyLong()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep2) mockZeebeCommand).and()).thenReturn(mockZeebeCommand);
    when(((ModifyProcessInstanceCommandStep3) mockZeebeCommand)
            .withVariables(anyMap(), anyString()))
        .thenReturn((ModifyProcessInstanceCommandStep3) mockZeebeCommand);
  }

  @Test
  public void testZeroNewTokensDeclared() {
    final Modification modification =
        new Modification()
            .setNewTokensCount(0)
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testZeroNewTokensCalculated() {
    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of());

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verify(mockFlowNodeInstanceReader, times(1))
        .getFlowNodeInstanceKeysByIdAndStates(
            123L, modification.getFromFlowNodeId(), List.of(FlowNodeState.ACTIVE));
  }

  @Test
  public void testNoFromFlowNodeSpecified() {
    final Modification modification =
        new Modification().setModification(Type.MOVE_TOKEN).setToFlowNodeId("taskB");

    assertThat(moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification)).isNull();
    verifyNoInteractions(mockZeebeCommand);
    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testMoveToken() {
    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeInstanceKey("888")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(888L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verifyNoInteractions(mockFlowNodeInstanceReader);
  }

  @Test
  public void testMoveTokenWithVariables() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB")
            .setVariables(
                Map.of(
                    "taskB", List.of(Map.of("a", "b")),
                    "process", List.of(Map.of("c", "d"))));

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(5);
    verify(mockZeebeCommand, times(1)).activateElement("taskB");
    verify((ModifyProcessInstanceCommandStep3) mockZeebeCommand, times(1))
        .withVariables(Map.of("c", "d"), "process");
    verify((ModifyProcessInstanceCommandStep3) mockZeebeCommand, times(1))
        .withVariables(Map.of("a", "b"), "taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(456L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verify(mockFlowNodeInstanceReader, times(2))
        .getFlowNodeInstanceKeysByIdAndStates(123L, "taskA", List.of(FlowNodeState.ACTIVE));
  }

  @Test
  public void testMoveMultipleTokens() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L, 789L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setFromFlowNodeId("taskA")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(7);
    verify(mockZeebeCommand, times(2)).activateElement("taskB");
    verify(mockZeebeCommand, times(1)).terminateElement(456L);
    verify(mockZeebeCommand, times(1)).terminateElement(789);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(3)).and();
  }

  @Test
  public void testMoveTokenWithAncestor() {
    when(mockFlowNodeInstanceReader.getFlowNodeInstanceKeysByIdAndStates(
            123L, "taskA", List.of(FlowNodeState.ACTIVE)))
        .thenReturn(List.of(456L));

    final Modification modification =
        new Modification()
            .setModification(Type.MOVE_TOKEN)
            .setAncestorElementInstanceKey(999L)
            .setFromFlowNodeInstanceKey("888")
            .setToFlowNodeId("taskB");

    final ModifyProcessInstanceCommandStep1.ModifyProcessInstanceCommandStep2 result =
        moveTokenHandler.moveToken(mockZeebeCommand, 123L, modification);

    assertThat(result).isNotNull();

    assertThat(Mockito.mockingDetails(mockZeebeCommand).getInvocations()).hasSize(3);
    verify(mockZeebeCommand, times(1)).activateElement("taskB", 999L);
    verify(mockZeebeCommand, times(1)).terminateElement(888L);
    verify((ModifyProcessInstanceCommandStep2) mockZeebeCommand, times(1)).and();

    verifyNoInteractions(mockFlowNodeInstanceReader);
  }
}
