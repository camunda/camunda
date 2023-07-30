/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */

package io.camunda.zeebe.engine.processing.signal;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.client.SignalClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

public class BroadcastSignalMultiplePartitionsTest {

  public static final String PROCESS_ID = "process";
  public static final int PARTITION_COUNT = 3;
  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

  private static final String SIGNAL_NAME = "a";

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  private final SignalClient signalClient = ENGINE.signal().withSignalName(SIGNAL_NAME);

  @Test
  public void shouldWriteDistributingRecordsForOtherPartitions() {
    // given
    final var process =
        Bpmn.createExecutableProcess(PROCESS_ID).startEvent().signal(SIGNAL_NAME).endEvent().done();
    // when
    ENGINE.deployment().withXmlResource(process).deploy();

    // then
    final var signalKey = signalClient.broadcast().getKey();
    final var commandDistributionRecords =
        RecordingExporter.commandDistributionRecords()
            .withIntent(CommandDistributionIntent.DISTRIBUTING)
            .valueFilter(v -> v.getValueType().equals(ValueType.SIGNAL))
            .limit(2)
            .asList();

    assertThat(commandDistributionRecords).extracting(Record::getKey).containsOnly(signalKey);

    assertThat(commandDistributionRecords)
        .extracting(Record::getValue)
        .extracting(CommandDistributionRecordValue::getPartitionId)
        .containsExactly(2, 3);
  }
}
