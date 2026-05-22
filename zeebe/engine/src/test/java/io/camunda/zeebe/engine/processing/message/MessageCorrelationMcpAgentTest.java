/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.message;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.MessageCorrelationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class MessageCorrelationMcpAgentTest {

  private static final String PROCESS_ID = "processWithMessageStartEvent";
  private static final String START_MSG_NAME = "startMsg";
  private static final String START_EVENT_ID = "msgStartEvent";
  private static final String MCP_TOOL_NAME = "mcp-test-tool";

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Before
  public void deployProcess() {
    engine
        .deployment()
        .withXmlResource(
            "process.bpmn",
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent(START_EVENT_ID)
                .message(m -> m.name(START_MSG_NAME))
                .endEvent()
                .done())
        .deploy();
  }

  @Test
  public void shouldPropagateToolNameOnCorrelationRecord() {
    // when
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withToolName(MCP_TOOL_NAME)
        .correlate();

    // then
    final var correlatedRecord =
        RecordingExporter.messageCorrelationRecords(MessageCorrelationIntent.CORRELATED)
            .withName(START_MSG_NAME)
            .getFirst();

    assertThat(correlatedRecord.getValue().getToolName()).isEqualTo(MCP_TOOL_NAME);
  }

  @Test
  public void shouldSetAgentInfoOnFollowUpRecordsWhenToolNameIsSet() {
    // when
    engine
        .messageCorrelation()
        .withName(START_MSG_NAME)
        .withCorrelationKey("")
        .withToolName(MCP_TOOL_NAME)
        .correlate();

    // then - the process instance created from the message start event carries agent tracing
    final var processActivatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivatingRecord.getAgent()).isNotNull();
    assertThat(processActivatingRecord.getAgent().getElementId()).isEqualTo(START_EVENT_ID);
    assertThat(processActivatingRecord.getAgent().getToolName()).isEqualTo(MCP_TOOL_NAME);
  }

  @Test
  public void shouldNotSetAgentInfoWhenToolNameIsAbsent() {
    // when
    engine.messageCorrelation().withName(START_MSG_NAME).withCorrelationKey("").correlate();

    // then
    final var processActivatingRecord =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withBpmnProcessId(PROCESS_ID)
            .withElementType(BpmnElementType.PROCESS)
            .getFirst();

    assertThat(processActivatingRecord.getAgent()).isNull();
  }
}
