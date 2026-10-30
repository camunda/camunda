/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceResultRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceResultIntent;
import io.camunda.zeebe.stream.api.CommandResponseWriter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public final class CreateProcessInstanceWithResultTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess("PROCESS").startEvent().endEvent().done();
  private static ProcessInstanceResultRecord response;
  private static CommandResponseWriter mockCommandResponseWriter;

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @BeforeClass
  public static void init() {
    mockCommandResponseWriter = ENGINE.getCommandResponseWriter();
    interceptResponseWriter(mockCommandResponseWriter);
    ENGINE.deployment().withXmlResource(PROCESS).deploy();
  }

  @Before
  public void reset() {
    Mockito.clearInvocations(mockCommandResponseWriter);
  }

  @Test
  public void shouldSendResultAfterCompletion() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS")
            .withVariables(Map.of("x", "foo"))
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .withTags(Set.of("tag1", "tag2"))
            .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(ProcessInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables()).containsExactly(Map.entry("x", "foo"));
    assertThat(response.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(response.getBpmnProcessId()).isEqualTo("PROCESS");
    assertThat(response.getTags()).isEqualTo(Set.of("tag1", "tag2"));
  }

  @Test
  public void shouldSendResultWithBusinessIdAfterCompletion() {
    // given
    final String businessId = "order-12345";
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS")
            .withVariables(Map.of("x", "foo"))
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .withBusinessId(businessId)
            .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(ProcessInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(response.getBpmnProcessId()).isEqualTo("PROCESS");
    assertThat(response.getBusinessId()).isEqualTo(businessId);
  }

  @Test
  public void shouldSendResultWithNoVariablesAfterCompletion() {
    // given
    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId("PROCESS")
            .withResult()
            .withRequestId(1L)
            .withRequestStreamId(1)
            .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(ProcessInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables()).isEmpty();
    assertThat(response.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(response.getBpmnProcessId()).isEqualTo("PROCESS");
  }

  @Test
  public void shouldSendRequestedVariablesAfterCompletion() {
    // given
    final Set<String> fetchVariables = Set.of("x", "y");

    ENGINE
        .processInstance()
        .ofBpmnProcessId("PROCESS")
        .withVariables(Map.of("x", "foo", "y", "bar", "z", "foo-bar"))
        .withResult()
        .withFetchVariables(fetchVariables)
        .withRequestId(1L)
        .withRequestStreamId(1)
        .create();

    // then
    verify(mockCommandResponseWriter, timeout(1000).times(1))
        .intent(ProcessInstanceResultIntent.COMPLETED);
    verify(mockCommandResponseWriter, timeout(1000).times(1)).tryWriteResponse(1, 1L);
    assertThat(response.getVariables())
        .containsExactlyInAnyOrderEntriesOf(Map.of("x", "foo", "y", "bar"));
  }

  @Test
  public void shouldSendRequestedVariablesWhenSomeAreNotAvailableAfterCompletion() {
    // given
    final Set<String> fetchVariables = Set.of("x", "none-existing");

    ENGINE
        .processInstance()
        .ofBpmnProcessId("PROCESS")
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
        .processInstance()
        .ofBpmnProcessId("INVALID-PROCESS")
        .withResult()
        .withRequestId(3L)
        .withRequestStreamId(3)
        .asyncCreate();

    // then
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1))
        .rejectionType(RejectionType.NOT_FOUND);
    verify(ENGINE.getCommandResponseWriter(), timeout(1000).times(1)).tryWriteResponse(3, 3);
  }

  private static void interceptResponseWriter(
      final CommandResponseWriter mockCommandResponseWriter) {
    doAnswer(
            (Answer<CommandResponseWriter>)
                (invocation -> {
                  final Object[] arguments = invocation.getArguments();
                  if (arguments != null
                      && arguments.length == 1
                      && arguments[0] != null
                      && arguments[0] instanceof ProcessInstanceResultRecord) {
                    response = (ProcessInstanceResultRecord) arguments[0];
                  }
                  return mockCommandResponseWriter;
                }))
        .when(mockCommandResponseWriter)
        .valueWriter(any());
  }
}
