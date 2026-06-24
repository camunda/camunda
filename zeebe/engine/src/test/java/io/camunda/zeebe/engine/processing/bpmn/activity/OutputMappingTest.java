/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.activity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.Protocol;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OutputMappingTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  private static final String PROCESS_ID = "processId";
  private final BpmnModelInstance bpmnModelInstance;
  private final String elementId;
  private final Consumer<Long> completionHandler;

  public OutputMappingTest(
      final BpmnModelInstance bpmnModelInstance,
      final String elementId,
      final Consumer<Long> completionHandler) {
    this.bpmnModelInstance = bpmnModelInstance;
    this.elementId = elementId;
    this.completionHandler = completionHandler;
  }

  @Parameters(name = "{index}: {1}")
  public static Collection<Object[]> parameters() {
    return Arrays.asList(
        new Object[][] {
          {
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .serviceTask(
                    "serviceTaskId",
                    b -> b.zeebeJobType("type").zeebeOutputExpression("foo", "bar"))
                .endEvent()
                .done(),
            "serviceTaskId",
            handler(key -> ENGINE.job().withType("type").ofInstance(key).complete())
          },
          {
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .intermediateThrowEvent(
                    "intermediateThrowEventId", b -> b.zeebeOutputExpression("foo", "bar"))
                .endEvent()
                .done(),
            "intermediateThrowEventId",
            handler(key -> {})
          },
          {
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("userTaskId", b -> b.zeebeOutputExpression("foo", "bar"))
                .endEvent()
                .done(),
            "userTaskId",
            handler(
                key ->
                    ENGINE.job().withType(Protocol.USER_TASK_JOB_TYPE).ofInstance(key).complete())
          },
          {
            Bpmn.createExecutableProcess(PROCESS_ID)
                .startEvent()
                .userTask("nativeUserTaskId", b -> b.zeebeOutputExpression("foo", "bar"))
                .zeebeUserTask()
                .endEvent()
                .done(),
            "nativeUserTaskId",
            handler(key -> ENGINE.userTask().ofInstance(key).complete())
          },
        });
  }

  @Test
  public void shouldApplyOutputMapping() {
    // given
    ENGINE.deployment().withXmlResource(bpmnModelInstance).deploy();

    final long processInstanceKey =
        ENGINE
            .processInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariables("{ \"foo\": 1 }")
            .create();

    // when
    completionHandler.accept(processInstanceKey);

    // then
    final Record<ProcessInstanceRecordValue> record =
        RecordingExporter.processInstanceRecords()
            .withElementId(elementId)
            .withIntent(ProcessInstanceIntent.ELEMENT_COMPLETED)
            .withProcessInstanceKey(processInstanceKey)
            .getFirst();
    final Map<String, String> variables =
        ProcessInstances.getCurrentVariables(processInstanceKey, record.getPosition());
    assertThat(variables).contains(entry("bar", "1"));
  }

  private static Consumer<Long> handler(final Consumer<Long> handler) {
    return handler;
  }
}
