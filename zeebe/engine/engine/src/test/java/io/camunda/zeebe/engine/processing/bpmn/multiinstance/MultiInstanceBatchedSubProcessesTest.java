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

  /**
   * Looking at the comments next to the PROCESS creation step, you will notice that the PI_BATCH
   * Activate will come at the position number 6 in the command batch. (CREATE PROCESS, Activate
   * PROCESS, ACTIVATE START_EVENT, COMPLETE START_EVENT, ACTIVATE MULTI_INSTANCE_BODY, ACTIVATE
   * PI_BATCH)
   *
   * <p>We also had to create a sub process small enough that could be completed and trigger
   * completing the parent in less than 6 commands to be contained in the same batch together to be
   * able to reproduce the case. That's why in the example the sub process has only a `startEvent`,
   * so we are able to (ACTIVATE SUB_PROCESS, ACTIVATE START_EVENT, COMPLETE START_EVENT, COMPLETE
   * SUB_PROCESS, COMPLETE MULTI_INSTANCE_BODY) together in the next command batch.
   */
  public static final int MAX_COMMANDS_IN_BATCH = 6;

  @ClassRule
  public static final EngineRule ENGINE =
      EngineRule.singlePartition().maxCommandsInBatch(MAX_COMMANDS_IN_BATCH);

  private static final String PROCESS_ID = "process";
  private static final String SUB_PROCESS_START = "sub-process-start";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID) // Generates 2 Commands (CREATE, Activate Process)
          .startEvent() // Generates 2 Commands ( ACTIVATE, COMPLETE START_EVENT)
          .zeebeOutputExpression("= [1,2]", "inputCollection")
          .subProcess(
              "subprocess1",
              s ->
                  s.multiInstance( // Generates 1 Command (ACTIVATE MULTI_INSTANCE_BODY)
                      b ->
                          b.zeebeInputCollectionExpression("inputCollection")
                              // This will result in 1 command (PI_BATCH ACTIVATE)
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
            RecordingExporter.processInstanceRecords()
                .limitToProcessInstanceCompleted()
                .withElementId(SUB_PROCESS_START)
                .withIntent(ProcessInstanceIntent.ELEMENT_ACTIVATED)
                .toList())
        .hasSize(count);
  }
}
