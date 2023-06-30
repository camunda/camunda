/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.processinstance;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.Strings;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public final class CancelProcessInstanceBanTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test // Regression of https://github.com/camunda/zeebe/issues/8955
  public void shouldBanInstanceWhenTerminatingInstanceWithALotOfNestedChildInstances() {
    // given
    final var amountOfNestedChildInstances = 1000;
    final var processId = Strings.newRandomValidBpmnId();
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .exclusiveGateway()
                .defaultFlow()
                .userTask()
                .endEvent()
                .moveToLastGateway()
                .conditionExpression("count < " + amountOfNestedChildInstances)
                .intermediateThrowEvent("preventStraightThroughLoop")
                .callActivity(
                    "callActivity",
                    c -> c.zeebeProcessId(processId).zeebeInputExpression("count + 1", "count"))
                .endEvent()
                .done())
        .deploy();

    final long processInstanceKey =
        ENGINE.processInstance().ofBpmnProcessId(processId).withVariable("count", 0).create();

    RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATED)
        .withElementType(BpmnElementType.USER_TASK)
        .getFirst();

    // when
    final var errorRecordValue =
        ENGINE.processInstance().withInstanceKey(processInstanceKey).cancelWithError();

    // then
    Assertions.assertThat(errorRecordValue.getValue().getStacktrace())
        .contains("ChildTerminationStackOverflowException");
    Assertions.assertThat(errorRecordValue.getValue().getExceptionMessage())
        .contains(
            "Process instance",
            """
            has too many nested child instances and could not be terminated. The deepest nested \
            child instance has been banned as a result.""");
  }
}
