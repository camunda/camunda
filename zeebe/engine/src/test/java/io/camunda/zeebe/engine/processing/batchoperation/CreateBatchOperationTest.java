/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.BrokerClassRuleHelper;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.Rule;
import org.junit.Test;

public final class CreateBatchOperationTest {

  @Rule
  public final RecordingExporterTestWatcher recordingExporterTestWatcher =
      new RecordingExporterTestWatcher();

  @Rule public final BrokerClassRuleHelper helper = new BrokerClassRuleHelper();

  @Rule public final EngineRule engine = EngineRule.singlePartition();

  @Test
  public void shouldRejectWithoutFilter() {
    // when
    final var batchOperationKey =
        engine
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .withFilter(new UnsafeBuffer())
            .expectRejection()
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    final var result =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(result.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(result.getIntent()).isEqualTo(BatchOperationIntent.CREATE);
  }

  @Test
  public void shouldRejectWithEmptyFilter() {
    // when
    final var batchOperationKey =
        engine
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .withFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack("{}")))
            .expectRejection()
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    final var result =
        RecordingExporter.batchOperationCreationRecords()
            .withBatchOperationKey(batchOperationKey)
            .onlyCommandRejections()
            .getFirst();

    assertThat(result.getRejectionType()).isEqualTo(RejectionType.INVALID_ARGUMENT);
    assertThat(result.getIntent()).isEqualTo(BatchOperationIntent.CREATE);
  }

  @Test
  public void shouldCreateBatchOperation() {
    // given
    final String filter = "{\"processInstanceKeys\":[1,3,8]}";

    // when
    final long batchOperationKey =
        engine
            .batchOperation()
            .ofType(BatchOperationType.PROCESS_CANCELLATION)
            .withFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)))
            .create()
            .getValue()
            .getBatchOperationKey();

    // then
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey))
        .extracting(Record::getIntent)
        .containsSequence(BatchOperationIntent.CREATED);
    assertThat(
            RecordingExporter.batchOperationCreationRecords()
                .withBatchOperationKey(batchOperationKey)
                .withIntent(BatchOperationIntent.CREATED)
                .getFirst()
                .getValue()
                .getEntityFilter())
        .isEqualTo(filter);
  }
}
