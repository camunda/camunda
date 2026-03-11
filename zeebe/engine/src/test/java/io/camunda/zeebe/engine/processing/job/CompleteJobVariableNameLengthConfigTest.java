/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.job;

import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.impl.record.value.job.JobRecord;
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.time.Duration;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteJobVariableNameLengthConfigTest {

  private static final int CUSTOM_MAX_NAME_FIELD_LENGTH = 5;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setMaxNameFieldLength(CUSTOM_MAX_NAME_FIELD_LENGTH));

  @Test
  public void shouldCompleteJobIfVariableNameExceedsConfiguredMaxLength() {
    // given
    final var jobType = "test";
    final var processId = "proc";
    engine
        .deployment()
        .withXmlResource(
            processId + ".bpmn",
            Bpmn.createExecutableProcess(processId)
                .startEvent("start")
                .serviceTask("task", b -> b.zeebeJobType(jobType).done())
                .endEvent("end")
                .done())
        .deploy();
    engine.processInstance().ofBpmnProcessId(processId).create();
    RecordingExporter.await(Duration.ofSeconds(30))
        .until(() -> RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).exists());
    final var createdJobRecord =
        RecordingExporter.jobRecords(JobIntent.CREATED).withType(jobType).getFirst();
    final var jobKey = createdJobRecord.getKey();
    final String variableName = "x".repeat(CUSTOM_MAX_NAME_FIELD_LENGTH + 1);

    // when
    final var completeCommand = new JobRecord().setVariables(asMsgPack(variableName, "bar"));
    engine.writeRecords(
        RecordToWrite.command().key(jobKey).job(JobIntent.COMPLETE, completeCommand));

    RecordingExporter.await(Duration.ofSeconds(30))
        .until(
            () ->
                RecordingExporter.jobRecords(JobIntent.COMPLETED)
                    .withRecordKey(jobKey)
                    .exists());
    final var completedRecord =
        RecordingExporter.jobRecords(JobIntent.COMPLETED).withRecordKey(jobKey).getFirst();

    // then
    Assertions.assertThat(completedRecord).hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).containsEntry(variableName, "bar");
  }
}
