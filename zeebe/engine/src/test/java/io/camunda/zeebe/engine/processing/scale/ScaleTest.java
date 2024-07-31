/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.scale;

import static io.camunda.zeebe.protocol.Protocol.START_PARTITION_ID;
import static io.camunda.zeebe.test.util.MsgPackUtil.asMsgPack;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.MapEntry.entry;
import static org.junit.Assert.fail;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.engine.util.RecordToWrite;
import io.camunda.zeebe.engine.util.client.ProcessInstanceClient.ProcessInstanceCreationClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.protocol.impl.record.value.scale.ScaleRecord;
import io.camunda.zeebe.protocol.record.intent.ScaleIntent;
import io.camunda.zeebe.test.util.collection.Maps;
import io.camunda.zeebe.test.util.record.ProcessInstances;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;

public class ScaleTest {
  private static final Map<Integer, String> CORRELATION_KEYS =
      Maps.of(
          entry(START_PARTITION_ID, "item-2"),
          entry(START_PARTITION_ID + 1, "item-1"),
          entry(START_PARTITION_ID + 2, "item-0"));
  private static final String PROCESS_ID = "process";
  private static final BpmnModelInstance PROCESS =
      Bpmn.createExecutableProcess(PROCESS_ID)
          .startEvent()
          .intermediateCatchEvent("receive-message")
          .message(m -> m.name("message").zeebeCorrelationKeyExpression("key"))
          .endEvent("end")
          .done();
  @Rule public final EngineRule engine = new EngineRule(3, 1);

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
  }

  @Test
  public void shouldMoveMessageSubscription() {
    // given
    engine.deployment().withXmlResource(PROCESS).deploy();
    // create instance with intermediate message catch event
    final ProcessInstanceCreationClient processInstanceCreationClient =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID);
    final var processInstanceKey1 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
            .create();
    final var processInstanceKey2 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
            .create();
    final var processInstanceKey3 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
            .create();

    // scale
    final var scaleRecord = new ScaleRecord();
    scaleRecord.setRoutingInfo(1, 3);

    // when
    engine.writeRecords(
        RecordToWrite.command().key(-1).scale(ScaleIntent.RELOCATION_START, scaleRecord));

    // then
    assertThat(
            RecordingExporter.records()
                .filter(r -> r.getIntent().equals(ScaleIntent.RELOCATION_COMPLETED))
                .limit(3))
        .describedAs("Expect relocation to be completed")
        .isNotEmpty();

    // when
    // publish messages

    engine.forEachPartition(
        partitionId ->
            engine
                .message()
                .onPartition(partitionId)
                .withName("message")
                .withCorrelationKey(CORRELATION_KEYS.get(partitionId))
                .withVariables(asMsgPack("p", "p" + partitionId))
                .publish());

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            ProcessInstances.getCurrentVariables(processInstanceKey1).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey2).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey3).get("p"));

    assertThat(correlatedValues).contains("\"p1\"", "\"p2\"", "\"p3\"");
  }

  @Test
  public void shouldMoveMessages() {
    // given
    engine.forEachPartition(
        partitionId ->
            engine
                .message()
                .onPartition(1)
                .withName("message")
                .withCorrelationKey(CORRELATION_KEYS.get(partitionId))
                .withVariables(asMsgPack("p", "p" + partitionId))
                .publish());

    // scale
    final var scaleRecord = new ScaleRecord();
    scaleRecord.setRoutingInfo(1, 3);

    // when
    engine.writeRecords(
        RecordToWrite.command().key(-1).scale(ScaleIntent.RELOCATION_START, scaleRecord));

    // then
    assertThat(
            RecordingExporter.records()
                .filter(r -> r.getIntent().equals(ScaleIntent.RELOCATION_COMPLETED))
                .limit(3))
        .describedAs("Expect relocation to be completed")
        .isNotEmpty();

    // when
    engine.deployment().withXmlResource(PROCESS).deploy();
    // create instance with intermediate message catch event
    final ProcessInstanceCreationClient processInstanceCreationClient =
        engine.processInstance().ofBpmnProcessId(PROCESS_ID);
    final var processInstanceKey1 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID))
            .create();
    final var processInstanceKey2 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 1))
            .create();
    final var processInstanceKey3 =
        processInstanceCreationClient
            .withVariable("key", CORRELATION_KEYS.get(START_PARTITION_ID + 2))
            .create();

    // then
    final List<String> correlatedValues =
        Arrays.asList(
            ProcessInstances.getCurrentVariables(processInstanceKey1).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey2).get("p"),
            ProcessInstances.getCurrentVariables(processInstanceKey3).get("p"));

    assertThat(correlatedValues).contains("\"p1\"", "\"p2\"", "\"p3\"");

    fail();
  }
}
