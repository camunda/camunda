/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.globallistener;

import static io.camunda.zeebe.protocol.record.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerBatchRecord;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class GlobalListenersInitializationTest {

  @Rule public final EngineRule engine = EngineRule.singlePartition();
  @Rule public final TestWatcher recordingExporterTestWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldConfigureGlobalListenersThroughCommand() {
    // given a global listener batch record containing the desired configuration
    final GlobalListenerBatchRecord record =
        new GlobalListenerBatchRecord()
            .addTaskListener(new GlobalListenerRecord().setType("global1").addEventType("all"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setType("global2")
                    .addEventType("creating")
                    .addEventType("completing")
                    .setAfterNonGlobal(true));

    // when executing a CONFIGURE command with the record
    engine.writeRecords(
        RecordToWrite.command().globalListenerBatch(GlobalListenerBatchIntent.CONFIGURE, record));
    RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED).await();

    // then the engine's processing state contains the expected configuration
    final GlobalListenerBatchRecord currentConfig =
        engine.getProcessingState().getGlobalListenersState().getCurrentConfig();
    assertThat(currentConfig).isNotNull();
    // set key on record to be able to compare
    record.setGlobalListenerBatchKey(currentConfig.getGlobalListenerBatchKey());
    assertThat(currentConfig).isEqualTo(record);
  }
}
