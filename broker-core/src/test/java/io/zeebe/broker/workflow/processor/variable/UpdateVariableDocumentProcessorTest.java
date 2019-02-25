/*
 * Zeebe Broker Core
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
package io.zeebe.broker.workflow.processor.variable;

import static io.zeebe.msgpack.value.DocumentValue.EMPTY_DOCUMENT;
import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.broker.logstreams.processor.CommandProcessor.CommandControl;
import io.zeebe.broker.logstreams.processor.TypedRecord;
import io.zeebe.broker.util.MockTypedRecord;
import io.zeebe.broker.util.ZeebeStateRule;
import io.zeebe.broker.workflow.state.ElementInstance;
import io.zeebe.broker.workflow.state.ElementInstanceState;
import io.zeebe.broker.workflow.state.VariablesState;
import io.zeebe.msgpack.spec.MsgpackReaderException;
import io.zeebe.protocol.VariableDocumentUpdateSemantic;
import io.zeebe.protocol.clientapi.RecordType;
import io.zeebe.protocol.clientapi.RejectionType;
import io.zeebe.protocol.clientapi.ValueType;
import io.zeebe.protocol.impl.record.RecordMetadata;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.intent.VariableDocumentIntent;
import io.zeebe.protocol.intent.WorkflowInstanceIntent;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@SuppressWarnings("unchecked")
public class UpdateVariableDocumentProcessorTest {
  @Rule public ZeebeStateRule zeebeStateRule = new ZeebeStateRule();

  private final CommandControl<VariableDocumentRecord> controller = mock(CommandControl.class);
  private ElementInstanceState elementInstanceState;
  private VariablesState variablesState;
  private UpdateVariableDocumentProcessor processor;

  @Before
  public void setUp() {
    elementInstanceState =
        zeebeStateRule.getZeebeState().getWorkflowState().getElementInstanceState();
    variablesState =
        spy(
            zeebeStateRule
                .getZeebeState()
                .getWorkflowState()
                .getElementInstanceState()
                .getVariablesState());
    processor = new UpdateVariableDocumentProcessor(elementInstanceState, variablesState);
  }

  @After
  public void tearDown() {
    reset(variablesState, controller);
  }

  @Test
  public void shouldRejectIfNoScopeFound() {
    // given
    final TypedRecord<VariableDocumentRecord> command = newCommand();

    // when
    processor.onCommand(command, controller);

    // then
    assertRejection(RejectionType.NOT_FOUND);
  }

  @Test
  public void shouldRejectOnMsgpackReadError() {
    // given
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand();
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(new UnsafeBuffer());

    // when
    doThrow(MsgpackReaderException.class)
        .when(variablesState)
        .setVariablesFromDocument(anyLong(), any());
    processor.onCommand(command, controller);

    // then
    assertRejection(RejectionType.INVALID_ARGUMENT);
  }

  @Test
  public void shouldApplyPropagateUpdateOperation() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand();
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1)).setVariablesFromDocument(instance.getKey(), document);
  }

  @Test
  public void shouldApplyLocalUpdateOperation() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand();
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1)).setVariablesLocalFromDocument(instance.getKey(), document);
  }

  @Test
  public void shouldPublishUpdatedRecord() {
    // given
    final DirectBuffer document = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand();
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setDocument(document);

    // when
    processor.onCommand(command, controller);

    // then
    assertAccepted(command);
  }

  private ElementInstance newElementInstance() {
    return elementInstanceState.newInstance(
        1, new WorkflowInstanceRecord(), WorkflowInstanceIntent.ELEMENT_ACTIVATED);
  }

  private void assertAccepted(TypedRecord<VariableDocumentRecord> command) {
    verify(controller, times(1)).accept(VariableDocumentIntent.UPDATED, command.getValue());
    verify(controller, never()).reject(any(), anyString());
  }

  private void assertRejection(RejectionType type) {
    verify(controller, never()).accept(any(), any());
    verify(controller, times(1)).reject(eq(type), anyString());
  }

  private TypedRecord<VariableDocumentRecord> newCommand() {
    final RecordMetadata metadata =
        new RecordMetadata()
            .intent(VariableDocumentIntent.UPDATE)
            .valueType(ValueType.VARIABLE_DOCUMENT)
            .recordType(RecordType.COMMAND);
    final VariableDocumentRecord value =
        new VariableDocumentRecord().setScopeKey(-1).setDocument(EMPTY_DOCUMENT);

    return new MockTypedRecord<>(-1, metadata, value);
  }
}
