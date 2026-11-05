/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.storageordinals;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.intent.MultiInstanceIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;

public final class MultiInstanceStorageOrdinalTest {

  private static final int FIXED_ORDINAL = 7;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .maxCommandsInBatch(1)
          .withEngineConfig(
              c -> c.setArchiverlessEnabled(true).setFixedStorageOrdinal(FIXED_ORDINAL));

  @Test
  public void shouldAssignInstanceOrdinalToInputCollectionEvaluatedEvent() {
    // given
    final var processId = "multiInstance-ordinal-input-collection";
    engine
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess(processId)
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType("task")
                            .multiInstance(
                                m -> m.parallel().zeebeInputCollectionExpression("items")))
                .endEvent()
                .done())
        .deploy();

    // when
    final var processInstanceKey =
        engine
            .processInstance()
            .ofBpmnProcessId(processId)
            .withVariable("items", List.of(1, 2))
            .create();

    // then
    final var multiInstanceBodyKey =
        RecordingExporter.processInstanceRecords(ProcessInstanceIntent.ELEMENT_ACTIVATING)
            .withProcessInstanceKey(processInstanceKey)
            .withElementType(BpmnElementType.MULTI_INSTANCE_BODY)
            .getFirst()
            .getKey();
    final var inputCollectionEvaluated =
        RecordingExporter.multiInstanceRecords(MultiInstanceIntent.INPUT_COLLECTION_EVALUATED)
            .filter(r -> r.getKey() == multiInstanceBodyKey)
            .getFirst();

    assertThat(inputCollectionEvaluated.getValue().getStorageOrdinal()).isEqualTo(FIXED_ORDINAL);
  }
}
