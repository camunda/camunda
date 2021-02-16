/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.deployment;

import static io.zeebe.test.util.TestUtil.waitUntil;
import static io.zeebe.util.buffer.BufferUtil.wrapString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.zeebe.el.ExpressionLanguageFactory;
import io.zeebe.engine.processing.common.CatchEventBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.message.command.SubscriptionCommandSender;
import io.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.zeebe.engine.processing.streamprocessor.writers.StateWriter;
import io.zeebe.engine.processing.streamprocessor.writers.TypedStreamWriter;
import io.zeebe.engine.processing.streamprocessor.writers.Writers;
import io.zeebe.engine.state.immutable.WorkflowState;
import io.zeebe.engine.util.StreamProcessorRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.Protocol;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.RecordType;
import io.zeebe.protocol.record.intent.DeploymentIntent;
import java.util.List;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

public final class TransformingDeploymentCreateProcessorTest {

  @Rule
  public final StreamProcessorRule rule = new StreamProcessorRule(Protocol.DEPLOYMENT_PARTITION);

  private WorkflowState workflowState;
  private SubscriptionCommandSender mockSubscriptionCommandSender;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    mockSubscriptionCommandSender = mock(SubscriptionCommandSender.class);
    final var writersMock =
        new Writers(
            mock(TypedStreamWriter.class),
            mock(StateWriter.class),
            mock(CommandResponseWriter.class));
    when(mockSubscriptionCommandSender.openMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any(), any(), anyBoolean()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.correlateMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(), any()))
        .thenReturn(true);
    when(mockSubscriptionCommandSender.closeMessageSubscription(
            anyInt(), anyLong(), anyLong(), any(DirectBuffer.class)))
        .thenReturn(true);

    rule.startTypedStreamProcessor(
        (typedRecordProcessors, processingContext) -> {
          final var zeebeState = processingContext.getZeebeState();
          workflowState = zeebeState.getWorkflowState();

          final var variablesState = zeebeState.getVariableState();
          final ExpressionProcessor expressionProcessor =
              new ExpressionProcessor(
                  ExpressionLanguageFactory.createExpressionLanguage(),
                  variablesState::getVariable);

          DeploymentEventProcessors.addTransformingDeploymentProcessor(
              typedRecordProcessors,
              zeebeState,
              new CatchEventBehavior(
                  zeebeState, expressionProcessor, mockSubscriptionCommandSender, 1),
              expressionProcessor,
              1,
              writersMock);
          return typedRecordProcessors;
        });
  }

  @Test
  public void shouldCreateDeploymentAndAddToWorkflowCache() {
    // given

    // when
    creatingDeployment();

    // then
    waitUntil(() -> rule.events().onlyDeploymentRecords().count() >= 2);

    final List<Record<DeploymentRecord>> collect =
        rule.events().onlyDeploymentRecords().collect(Collectors.toList());

    Assertions.assertThat(collect)
        .extracting(Record::getIntent)
        .containsExactly(DeploymentIntent.CREATE, DeploymentIntent.CREATED);
    Assertions.assertThat(collect)
        .extracting(Record::getRecordType)
        .containsExactly(RecordType.COMMAND, RecordType.EVENT);

    Assertions.assertThat(workflowState.getWorkflows().size()).isEqualTo(1);
    Assertions.assertThat(workflowState.getWorkflowsByBpmnProcessId(wrapString("processId")))
        .isNotNull();
  }

  private void creatingDeployment() {
    creatingDeployment(4);
  }

  private void creatingDeployment(final long key) {
    final BpmnModelInstance modelInstance =
        Bpmn.createExecutableProcess("processId")
            .startEvent()
            .serviceTask(
                "test",
                task -> {
                  task.zeebeJobType("type");
                })
            .endEvent()
            .done();

    final DeploymentRecord deploymentRecord = new DeploymentRecord();
    deploymentRecord
        .resources()
        .add()
        .setResourceName(wrapString("process.bpmn"))
        .setResource(wrapString(Bpmn.convertToString(modelInstance)));

    rule.writeCommand(key, DeploymentIntent.CREATE, deploymentRecord);
  }
}
