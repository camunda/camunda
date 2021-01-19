/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.engine.util.EngineRule;
import io.zeebe.model.bpmn.Bpmn;
import io.zeebe.model.bpmn.BpmnModelInstance;
import io.zeebe.protocol.record.Record;
import io.zeebe.protocol.record.intent.VariableIntent;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.VariableRecordValue;
import io.zeebe.test.util.JsonUtil;
import io.zeebe.test.util.record.RecordingExporter;
import io.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
@Ignore(
    value =
        "Ignored as part of the spike: we implemented 'activate element' only for outgoing flows, not for containers to keep the change small'")
public final class MultiInstanceInputCollectionTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  private static final String PROCESS_ID = "process";
  private static final String ELEMENT_ID = "task";
  private static final String INPUT_COLLECTION = "items";
  private static final String INPUT_ELEMENT = "item";

  private static final BpmnModelInstance WORKFLOW =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .serviceTask(
              ELEMENT_ID,
              t ->
                  t.zeebeJobType("test")
                      .multiInstance(
                          b ->
                              b.zeebeInputCollectionExpression(INPUT_COLLECTION)
                                  .zeebeInputElement(INPUT_ELEMENT)))
          .endEvent()
          .done();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Parameterized.Parameter(0)
  public Collection<?> inputCollection;

  @Parameterized.Parameters(name = "with input collection: {0}")
  public static Object[][] parameters() {
    return new Object[][] {
      {Arrays.asList("a")},
      {Arrays.asList(true, false)},
      {Arrays.asList(10, 20, 30)},
      {
        Arrays.asList(
            Collections.singletonMap("x", 1),
            Collections.singletonMap("x", 2),
            Collections.singletonMap("x", 3))
      },
      {Arrays.asList("task", null, true, 40)},
    };
  }

  @Test
  public void test() {
    // given
    ENGINE.deployment().withXmlResource(WORKFLOW).deploy();

    // when
    final long workflowInstanceKey =
        ENGINE
            .workflowInstance()
            .ofBpmnProcessId(PROCESS_ID)
            .withVariable(INPUT_COLLECTION, inputCollection)
            .create();

    // then
    final int collectionSize = inputCollection.size();

    final List<Long> elementInstanceKeys =
        RecordingExporter.workflowInstanceRecords(WorkflowInstanceIntent.ELEMENT_ACTIVATED)
            .withElementId(ELEMENT_ID)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(collectionSize + 1)
            .map(Record::getKey)
            .collect(Collectors.toList());

    assertThat(elementInstanceKeys).hasSize(collectionSize + 1);

    // and
    final List<Record<VariableRecordValue>> variables =
        RecordingExporter.variableRecords(VariableIntent.CREATED)
            .withName(INPUT_ELEMENT)
            .withWorkflowInstanceKey(workflowInstanceKey)
            .limit(collectionSize)
            .asList();

    final List<String> expectedVariableValues =
        inputCollection.stream().map(JsonUtil::toJson).collect(Collectors.toList());

    assertThat(variables)
        .extracting(r -> r.getValue().getValue())
        .containsExactlyElementsOf(expectedVariableValues);

    assertThat(variables)
        .extracting(r -> r.getValue().getScopeKey())
        .containsExactlyElementsOf(elementInstanceKeys.subList(1, elementInstanceKeys.size()));
  }
}
