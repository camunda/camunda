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
import io.camunda.zeebe.protocol.record.Assertions;
import io.camunda.zeebe.protocol.record.intent.JobIntent;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public final class CompleteJobVariableNameLengthConfigTest {

  private static final int CUSTOM_MAX_NAME_FIELD_LENGTH = 5;

  @Rule
  public final EngineRule engine =
      EngineRule.singlePartition()
          .withEngineConfig(config -> config.setMaxNameFieldLength(CUSTOM_MAX_NAME_FIELD_LENGTH));

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteJobIfVariableNameExceedsConfiguredMaxLength() {
    // given
    final var jobType = "test";
    final var processId = "process";
    engine.createJob(jobType, processId);
    final var activatedBatch = engine.jobs().withType(jobType).activate().getValue();
    final var jobKey = activatedBatch.getJobKeys().get(0);
    final String variableName = "x".repeat(CUSTOM_MAX_NAME_FIELD_LENGTH + 1);

    // when
    final var completedRecord =
        engine.job().withKey(jobKey).withVariables(asMsgPack(variableName, "bar")).complete();

    // then
    Assertions.assertThat(completedRecord).hasIntent(JobIntent.COMPLETED);
    assertThat(completedRecord.getValue().getVariables()).containsEntry(variableName, "bar");
  }
}
