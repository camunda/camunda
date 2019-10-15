/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.instance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.zeebe.engine.processor.CommandResponseWriter;
import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceResultRecord;
import io.zeebe.protocol.record.RejectionType;
import io.zeebe.protocol.record.intent.WorkflowInstanceResultIntent;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class CreateWorkflowInstanceWithResultTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess("WORKFLOW").startEvent().endEvent().done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private WorkflowInstanceResultRecord response;

  @BeforeClass
  public static void init() {
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();
  }

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    final CommandResponseWriter mockCommandResponseWriter = ENGINE.getCommandResponseWriter();
    Mockito.clearInvocations(mockCommandResponseWriter);
    interceptResponseWriter(mockCommandResponseWriter);

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
  public void shouldSendRejectionImmediately() {
    Mockito.clearInvocations(ENGINE.getCommandResponseWriter());
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

  private void interceptResponseWriter(CommandResponseWriter mockCommandResponseWriter) {
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
