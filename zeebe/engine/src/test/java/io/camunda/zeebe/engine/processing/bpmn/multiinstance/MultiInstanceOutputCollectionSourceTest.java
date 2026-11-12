/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.multiinstance;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.protocol.record.intent.VariableIntent;
import io.camunda.zeebe.protocol.record.value.VariableRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

/** PoC for the variable history: output collection updates carry the slot they fill. */
public final class MultiInstanceOutputCollectionSourceTest {

  @ClassRule public static final EngineRule ENGINE = EngineRule.singlePartition();

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldTagOutputCollectionUpdateWithTheFilledSlot() {
    // given
    final String jobType = "task-" + UUID.randomUUID();
    final long processInstanceKey = createParallelMultiInstance(jobType);
    final List<Long> jobKeys =
        RecordingExporter.jobRecords(JobIntent.CREATED)
            .withProcessInstanceKey(processInstanceKey)
            .limit(3)
            .map(Record::getKey)
            .toList();

    // when
    for (final int slot : List.of(2, 0, 1)) {
      ENGINE
          .job()
          .withKey(jobKeys.get(slot))
          .withVariables(Map.of("result", "result-" + slot))
          .complete();
    }

    // then
    assertThat(
            RecordingExporter.variableRecords(VariableIntent.UPDATED)
                .withProcessInstanceKey(processInstanceKey)
                .withName("results")
                .limit(3)
                .map(record -> record.getValue().getSource().getCollectionIndex())
                .toList())
        .containsExactly(2, 0, 1);
  }

  @Test
  public void shouldNotTagOtherOutputCollectionWrites() {
    // given
    final String jobType = "task-" + UUID.randomUUID();
    final long processInstanceKey = createParallelMultiInstance(jobType);

    // when
    RecordingExporter.jobRecords(JobIntent.CREATED)
        .withProcessInstanceKey(processInstanceKey)
        .limit(3)
        .map(Record::getKey)
        .forEach(
            jobKey ->
                ENGINE.job().withKey(jobKey).withVariables(Map.of("result", jobKey)).complete());

    // then: the initialization and the copy to the process scope, around the 3 updates
    final List<Record<VariableRecordValue>> writes =
        RecordingExporter.variableRecords()
            .withProcessInstanceKey(processInstanceKey)
            .withName("results")
            .limit(5)
            .toList();
    assertThat(writes)
        .filteredOn(record -> record.getIntent() == VariableIntent.CREATED)
        .extracting(record -> record.getValue().getSource().getCollectionIndex())
        .containsExactly(-1, -1);
  }

  private static long createParallelMultiInstance(final String jobType) {
    ENGINE
        .deployment()
        .withXmlResource(
            Bpmn.createExecutableProcess("process")
                .startEvent()
                .serviceTask(
                    "task",
                    t ->
                        t.zeebeJobType(jobType)
                            .multiInstance(
                                m ->
                                    m.parallel()
                                        .zeebeInputCollectionExpression("[1, 2, 3]")
                                        .zeebeInputElement("item")
                                        .zeebeOutputElementExpression("result")
                                        .zeebeOutputCollection("results")))
                .endEvent()
                .done())
        .deploy();
    return ENGINE.processInstance().ofBpmnProcessId("process").create();
  }
}
