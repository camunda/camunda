/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;

public class ScaleTest {
  @Rule public final EngineRule engine = EngineRule.multiplePartition(3);

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Test
  public void shouldCompleteWithEmptyState() {
    // given
    final var scaleRecord = new ScaleRecord();
    scaleRecord.setRoutingInfo(1, 3);

    // when
    engine.writeRecords(
        RecordToWrite.command().key(-1).scale(ScaleIntent.RELOCATION_START, scaleRecord));

    // then

    assertThat(
            RecordingExporter.records()
                .limit(r -> r.getIntent().equals(ScaleIntent.RELOCATION_COMPLETED)))
        .describedAs("Expect relocation to be completed")
        .isNotEmpty();
    fail();
  }
}
