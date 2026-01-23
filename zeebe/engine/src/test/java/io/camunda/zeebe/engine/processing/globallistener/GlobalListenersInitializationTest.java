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
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerBatchIntent;
import io.camunda.zeebe.protocol.record.intent.GlobalListenerIntent;
import io.camunda.zeebe.protocol.record.value.GlobalListenerRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.assertj.core.api.Assertions;
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
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global1")
                    .setType("global1")
                    .addEventType("all"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
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

  @Test
  public void shouldReplaceGlobalListenersConfigurationThroughCommand() {
    // given an existing global listener configuration
    final GlobalListenerBatchRecord originalConfigRecord =
        new GlobalListenerBatchRecord()
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global1")
                    .setType("global1")
                    .addEventType("all"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
                    .setType("global2")
                    .addEventType("all"));
    engine.writeRecords(
        RecordToWrite.command()
            .globalListenerBatch(GlobalListenerBatchIntent.CONFIGURE, originalConfigRecord));
    RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED).await();

    // when executing a CONFIGURE command with a different configuration
    final GlobalListenerBatchRecord newConfigRecord =
        new GlobalListenerBatchRecord()
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
                    .setType("global2")
                    .addEventType("creating"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global3")
                    .setType("global3")
                    .addEventType("all"));
    RecordingExporter.reset();
    engine.writeRecords(
        RecordToWrite.command()
            .globalListenerBatch(GlobalListenerBatchIntent.CONFIGURE, newConfigRecord));
    RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED).await();

    // then the engine's processing state contains the new configuration
    final GlobalListenerBatchRecord currentConfig =
        engine.getProcessingState().getGlobalListenersState().getCurrentConfig();
    assertThat(currentConfig).isNotNull();
    // set key on record to be able to compare
    newConfigRecord.setGlobalListenerBatchKey(currentConfig.getGlobalListenerBatchKey());
    assertThat(currentConfig).isEqualTo(newConfigRecord);
  }

  @Test
  public void shouldRemapConfigureCommandToIndividualListenerChanges() {
    // given an existing global listener configuration
    final GlobalListenerBatchRecord originalConfigRecord =
        new GlobalListenerBatchRecord()
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global1")
                    .setType("global1")
                    .addEventType("all"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
                    .setType("global2")
                    .addEventType("all"));
    engine.writeRecords(
        RecordToWrite.command()
            .globalListenerBatch(GlobalListenerBatchIntent.CONFIGURE, originalConfigRecord));
    RecordingExporter.globalListenerBatchRecords(GlobalListenerBatchIntent.CONFIGURED).await();

    // when executing a CONFIGURE command with a different configuration
    final GlobalListenerBatchRecord newConfigRecord =
        new GlobalListenerBatchRecord()
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
                    .setType("global2")
                    .addEventType("creating"))
            .addTaskListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global3")
                    .setType("global3")
                    .addEventType("all"));
    RecordingExporter.reset();
    engine.writeRecords(
        RecordToWrite.command()
            .globalListenerBatch(GlobalListenerBatchIntent.CONFIGURE, newConfigRecord));

    // then individual listener create/update/delete commands were written
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(record -> record.getIntent().equals(GlobalListenerBatchIntent.CONFIGURED))
                .globalListenerRecords()
                .onlyCommands())
        .satisfiesExactlyInAnyOrder(
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.DELETE)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global1"),
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.UPDATE)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global2"),
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.CREATE)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global3"));
  }
}
