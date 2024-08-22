/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class MultiInstanceBatchedSubProcessesTest {

  @ClassRule
  public static final EngineRule ENGINE = EngineRule.singlePartition().maxCommandsInBatch(6);

  private static final String PROCESS_ID = "process";
  private static final String SUB_PROCESS_START = "sub-process-start";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .zeebeOutputExpression("= [1,2]", "inputCollection")
          .subProcess(
              "subprocess1",
              s ->
                  s.multiInstance(
                      b ->
                          b.zeebeInputCollectionExpression("inputCollection")
                              .zeebeInputElement("input")))
          .embeddedSubProcess()
          .startEvent(SUB_PROCESS_START)
          .subProcessDone()
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  // Regression test for https://github.com/camunda/camunda/issues/20958
  public void shouldCompleteAllChildInstancesBeforeCompletingTheMultiInstanceBody() {
    // given
    ENGINE.deployment().withXmlResource(PROCESS).deploy();
    final int count = 2;

    // when
    ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    // then
    Assertions.assertThat(
            RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .withElementId(SUB_PROCESS_START)
                .limit(count)
                .toList())
        .hasSize(count);
  }
}
