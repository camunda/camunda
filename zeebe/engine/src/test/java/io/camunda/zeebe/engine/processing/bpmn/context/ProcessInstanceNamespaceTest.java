/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.context;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import org.junit.ClassRule;
import org.junit.Test;

public class ProcessInstanceNamespaceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance BASE_MODEL =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent("startEvent")
          .zeebeOutputExpression("camunda.processInstance.key", "key")
          .endEvent()
          .done();

  @Test
  public void shouldMapTheProcessInstanceKey() {
    ENGINE.deployment().withXmlResource(BASE_MODEL).deploy();
    final long processInstanceKey = ENGINE.processInstance().ofBpmnProcessId(PROCESS_ID).create();

    assertThat(RecordingExporter.processInstanceRecords().limitToProcessInstanceCompleted().count())
        .isGreaterThan(0);
    final var record = RecordingExporter.variableRecords().withName("key").getFirst();

    assertThat(record.getValue().getValue()).isEqualTo(Long.toString(processInstanceKey));
  }
}
