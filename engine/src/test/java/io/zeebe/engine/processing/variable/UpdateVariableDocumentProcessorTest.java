/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.variable;

import static io.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processing.CommandProcessorTestCase;
import io.zeebe.engine.processing.streamprocessor.TypedRecord;
import io.zeebe.engine.state.ZeebeState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.VariableDocumentIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.VariableDocumentUpdateSemantic;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.StrictStubs.class)
public final class UpdateVariableDocumentProcessorTest
    extends CommandProcessorTestCase<VariableDocumentRecord> {

  public static final int WORKFLOW_KEY = -1;

  private MutableElementInstanceState elementInstanceState;
  private MutableVariableState variablesState;
  private UpdateVariableDocumentProcessor processor;

  @Before
  public void setUp() {
    final ZeebeState state = ZEEBE_STATE_RULE.getZeebeState();
    elementInstanceState = state.getElementInstanceState();
    variablesState = Mockito.spy(state.getVariableState());
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
        .setVariables(badDocument);
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
    final DirectBuffer variables = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setVariables(variables);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1))
        .setVariablesFromDocument(instance.getKey(), WORKFLOW_KEY, variables);
  }

  @Test
  public void shouldApplyLocalUpdateOperation() {
    // given
    final DirectBuffer variables = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.LOCAL)
        .setVariables(variables);

    // when
    processor.onCommand(command, controller);

    // then
    verify(variablesState, times(1))
        .setVariablesLocalFromDocument(instance.getKey(), WORKFLOW_KEY, variables);
  }

  @Test
  public void shouldPublishUpdatedRecord() {
    // given
    final DirectBuffer variables = asMsgPack("a", 1);
    final ElementInstance instance = newElementInstance();
    final TypedRecord<VariableDocumentRecord> command = newCommand(VariableDocumentRecord.class);
    command
        .getValue()
        .setScopeKey(instance.getKey())
        .setUpdateSemantics(VariableDocumentUpdateSemantic.PROPAGATE)
        .setVariables(variables);

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
