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
  public void shouldRemapConfigureCommandToIndividualListenerChanges() {
    // given an existing global listener configuration
    engine
        .globalListenerBatch()
        .withListener(
            new GlobalListenerRecord()
                .setId("GlobalListener_global1")
                .setType("global1")
                .addEventType("all"))
        .withListener(
            new GlobalListenerRecord()
                .setId("GlobalListener_global2")
                .setType("global2")
                .addEventType("all"))
        .configure();

    // when executing a CONFIGURE command with a different configuration
    final GlobalListenerBatchRecord newConfigRecord =
        new GlobalListenerBatchRecord()
            .addListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global2")
                    .setType("global2")
                    .addEventType("creating"))
            .addListener(
                new GlobalListenerRecord()
                    .setId("GlobalListener_global3")
                    .setType("global3")
                    .addEventType("all"));
    RecordingExporter.reset();
    engine.globalListenerBatch().withRecord(newConfigRecord).configure();

    // then individual listener create/update/delete commands were written
    Assertions.assertThat(
            RecordingExporter.records()
                .limit(record -> record.getIntent().equals(GlobalListenerBatchIntent.CONFIGURED))
                .globalListenerRecords()
                .onlyEvents())
        .satisfiesExactlyInAnyOrder(
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.DELETED)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global1"),
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.UPDATED)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global2"),
            r ->
                assertThat(r)
                    .hasIntent(GlobalListenerIntent.CREATED)
                    .extracting(Record<GlobalListenerRecordValue>::getValue)
                    .extracting(GlobalListenerRecordValue::getId)
                    .isEqualTo("GlobalListener_global3"));
  }
}
