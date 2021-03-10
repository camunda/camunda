/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.workflowinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processing.streamprocessor.writers.CommandResponseWriter;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public final class CreateWorkflowInstanceWithResultTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("WORKFLOW").startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private WorkflowInstanceResultRecord response;
  private CommandResponseWriter mockCommandResponseWriter;

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Before
  public void setUp() {
    mockCommandResponseWriter = ENGINE.getCommandResponseWriter();
    Mockito.clearInvocations(mockCommandResponseWriter);
    interceptResponseWriter(mockCommandResponseWriter);
  }

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW")
            .withVariables(Map.of("x", "foo"))
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables()).containsExactly(Map.entry("x", "foo"));
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
    assertThat(response.getBpmnProcessId()).isEqualTo("WORKFLOW");
  }

  @Test
  public void shouldSendResultWithNoVariablesAfterCompletion() {
    // given
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId("WORKFLOW")
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables()).isEmpty();
    assertThat(response.getWorkflowInstanceKey()).isEqualTo(workflowInstanceKey);
    assertThat(response.getBpmnProcessId()).isEqualTo("WORKFLOW");
  }

  @Test
  public void shouldSendRequestedVariablesAfterCompletion() {
    // given
    final Set<String> fetchVariables = Set.of("x", "y");

    ENGINE
        .workflowInstance()
        .ofBpmnProcessId("WORKFLOW")
        .withVariables(Map.of("x", "foo", "y", "bar", "z", "foo-bar"))
        .withResult()
        .withFetchVariables(fetchVariables)
        .withRequestId(1L)
        .withRequestStreamId(1)
        .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(WorkflowInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables())
        .containsExactlyInAnyOrderEntriesOf(Map.of("x", "foo", "y", "bar"));
  }

  @Test
  public void shouldSendRequestedVariablesWhenSomeAreNotAvailableAfterCompletion() {
    // given
    final Set<String> fetchVariables = Set.of("x", "none-existing");

    ENGINE
        .workflowInstance()
        .ofBpmnProcessId("WORKFLOW")
        .withVariables(Map.of("x", "foo", "y", "bar", "z", "foo-bar"))
        .withResult()
        .withFetchVariables(fetchVariables)
        .withRequestId(1L)
        .withRequestStreamId(1)
        .create();

    // then
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables()).containsExactlyInAnyOrderEntriesOf(Map.of("x", "foo"));
  }

  @Test
  public void shouldSendRejectionImmediately() {
    // when
    ENGINE
        .workflowInstance()
        .ofBpmnProcessId("INVALID-WORKFLOW")
        .withResult()
        .withRequestId(3L)
        .withRequestStreamId(3)
        .asyncCreate();

    // then
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .rejectionType(RejectionType.NOT_FOUND);
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1)).tryWriteResponse(3, 3);
  }

  private void interceptResponseWriter(final CommandResponseWriter mockCommandResponseWriter) {
    doAnswer(
            (Answer<CommandResponseWriter>)
                (invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] != null
                      && arguments[0] instanceof WorkflowInstanceResultRecord) {
                    response = (WorkflowInstanceResultRecord) arguments[0];
                  }
                  return mockCommandResponseWriter;
                }))
        .when(mockCommandResponseWriter)
        .valueWriter(any());
  }
}
