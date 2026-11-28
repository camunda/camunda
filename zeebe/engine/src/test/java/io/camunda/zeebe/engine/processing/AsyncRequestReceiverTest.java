/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.AsyncRequestIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/**
 * A request can be answered after leadership of its partition moved, so the broker that received it
 * is recorded with it.
 */
public final class AsyncRequestReceiverTest {

  private static final String LOCAL_MEMBER_ID = "zone-a_0";

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().withEngineConfig(c -> c.setLocalMemberId(LOCAL_MEMBER_ID));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldRecordTheBrokerThatReceivedTheRequest() {
    // given
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask("task", t -> t.zeebeJobType("task"))
                .endEvent()
                .done())
        .deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId("process").create();

    // when
    ENGINE.processInstance().withInstanceKey(processInstanceKey).cancel();

    // then
    assertThat(
            RecordingExporter.asyncRequestRecords()
                .withIntent(AsyncRequestIntent.RECEIVED)
                .withRequestScopeKey(processInstanceKey)
                .withRequestValueType(ValueType.PROCESS_INSTANCE)
                .withRequestIntent(ProcessInstanceIntent.CANCEL)
                .getFirst()
                .getValue()
                .getReceiverId())
        .isEqualTo(LOCAL_MEMBER_ID);
  }
}
