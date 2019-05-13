/*
 * Zeebe Workflow Engine
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.engine.processor.workflow.variable;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.CommandProcessorTestCase;
import io.zeebe.engine.processor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.ElementInstanceState;
import io.zeebe.engine.state.instance.VariablesState;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public class UpdateVariableDocumentProcessorTest
    extends CommandProcessorTestCase<VariableDocumentRecord> {

  public static final int WORKFLOW_KEY = -1;

  private ElementInstanceState elementInstanceState;
  private VariablesState variablesState;
  private UpdateVariableDocumentProcessor processor;

  @Before
  public void setUp() {
    final ZeebeState state = zeebeStateRule.getZeebeState();
    elementInstanceState = state.getWorkflowState().getElementInstanceState();
    variablesState = Mockito.spy(elementInstanceState.getVariablesState());
    processor = new UpdateVariableDocumentProcessor(elementInstanceState, variablesState);
  }

  @Test
  public void shouldRejectIfNoScopeFound() {
    // given
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command.getValue().setScopeKey(1);

    // when
    processor.onCommand(command, controller);

    // then
    refuteAccepted();
    assertRejected(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectOnMsgpackReadError() {
    // given
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    final MutableDirectBuffer badDocument = new UnsafeBuffer(asMsgPack("{}"));
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(badDocument);
    badDocument.putByte(0, (byte) 0); // overwrite map header

    // when
    processor.onCommand(command, controller);

    // then
    refuteAccepted();
    assertRejected(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldApplyPropagateUpdateOperation() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1))
        .setVariablesFromDocument(instance.getKey(), WORKFLOW_KEY, document);
  }

  @Test
  public void shouldApplyLocalUpdateOperation() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1))
        .setVariablesLocalFromDocument(instance.getKey(), WORKFLOW_KEY, document);
  }

  @Test
  public void shouldPublishUpdatedRecord() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    refuteRejected();
    assertAccepted(VariableDocumentIntent.UPDATED, command.getValue());
  }

  private ElementInstance newElementInstance() {
    return elementInstanceState.newInstance(
        1, new WorkflowInstanceRecord(), WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }
}
