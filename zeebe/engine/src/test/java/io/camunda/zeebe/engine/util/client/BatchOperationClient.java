/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.search.filter.FilterBase;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ErrorIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ErrorRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.Function;

public final class BatchOperationClient {

  private final CommandWriter writer;

  public BatchOperationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public BatchOperationCreationClient ofType(final BatchOperationType type) {
    return new BatchOperationCreationClient(writer, type);
  }

  public ExistingBatchOperationClient ofKey(final long batchOperationKey) {
    return new ExistingBatchOperationClient(writer, batchOperationKey);
  }

  public static class BatchOperationCreationClient {

    private static final Function<Long, Record<BatchOperationCreationRecordValue>>
        SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationCreationRecords()
                    .withIntent(BatchOperationIntent.CREATED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationCreationRecordValue>>
        REJECTION_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationCreationRecords()
                    .onlyCommandRejections()
                    .withIntent(BatchOperationIntent.CREATE)
                    .withSourceRecordPosition(position)
                    .getFirst();
    private static final int DEFAULT_PARTITION = 1;

    private final CommandWriter writer;
    private final BatchOperationCreationRecord batchOperationCreationRecord;
    private int partition = DEFAULT_PARTITION;

    private Function<Long, Record<BatchOperationCreationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    public BatchOperationCreationClient(final CommandWriter writer, final BatchOperationType type) {
      this.writer = writer;
      batchOperationCreationRecord = new BatchOperationCreationRecord();
      batchOperationCreationRecord.setBatchOperationType(type);
    }

    public BatchOperationCreationClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public BatchOperationCreationClient withFilter(final FilterBase filter) {
      batchOperationCreationRecord.setFilter(filter);
      return this;
    }

    public BatchOperationCreationWithResultClient withResult() {
      return new BatchOperationCreationWithResultClient(writer, batchOperationCreationRecord);
    }

    public long create() {
      return create(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public long create(final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(ProcessInstanceCreationIntent.CREATE)
                      .event(batchOperationCreationRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      final var resultingRecord = expectation.apply(position);
      return resultingRecord.getValue().getBatchOperationKey();
    }

    public long create(final String username) {
      return create(AuthorizationUtil.getAuthInfo(username));
    }

    public BatchOperationCreationClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }
  }

  public static class BatchOperationCreationWithResultClient {
    private final CommandWriter writer;
    private final BatchOperationCreationRecord record;
    private long requestId = 1L;
    private int requestStreamId = 1;

    public BatchOperationCreationWithResultClient(
        final CommandWriter writer, final BatchOperationCreationRecord record) {
      this.writer = writer;
      this.record = record;
    }

    public BatchOperationCreationWithResultClient withRequestId(final long requestId) {
      this.requestId = requestId;
      return this;
    }

    public BatchOperationCreationWithResultClient withRequestStreamId(final int requestStreamId) {
      this.requestStreamId = requestStreamId;
      return this;
    }

    public long create() {
      final long position =
          writer.writeCommand(requestStreamId, requestId, BatchOperationIntent.CREATE, record);

      return RecordingExporter.batchOperationCreationRecords()
          .withIntent(BatchOperationIntent.CREATED)
          .withSourceRecordPosition(position)
          .getFirst()
          .getValue()
          .getBatchOperationKey();
    }
  }

  public static class ExistingBatchOperationClient {

    public static final Function<Long, Record<BatchOperationExecutionRecordValue>>
        SUCCESS_EXPECTATION =
            (batchOperationKey) ->
                RecordingExporter.batchOperationExecutionRecords()
                    .withRecordKey(batchOperationKey)
                    .withIntent(BatchOperationIntent.COMPLETED)
                    .withBatchOperationKey(batchOperationKey)
                    .getFirst();

    public static final Function<Long, Record<BatchOperationExecutionRecordValue>>
        REJECTION_EXPECTATION =
            (batchOperationKey) ->
                RecordingExporter.batchOperationExecutionRecords()
                    .onlyCommandRejections()
                    .withIntent(BatchOperationIntent.CANCEL)
                    .withRecordKey(batchOperationKey)
                    .withBatchOperationKey(batchOperationKey)
                    .getFirst();

    public static final Function<Long, Record<ErrorRecordValue>> ERROR_EXPECTATION =
        (processInstanceKey) ->
            RecordingExporter.errorRecords().withIntent(ErrorIntent.CREATED).getFirst();

    private static final int DEFAULT_PARTITION = -1;
    private final CommandWriter writer;
    private final long batchOperationKey;

    private int partition = DEFAULT_PARTITION;
    private Function<Long, Record<BatchOperationExecutionRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    public ExistingBatchOperationClient(final CommandWriter writer, final long batchOperationKey) {
      this.writer = writer;
      this.batchOperationKey = batchOperationKey;
    }

    public ExistingBatchOperationClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public ExistingBatchOperationClient expectRejection() {
      expectation = REJECTION_EXPECTATION;
      return this;
    }

    public Record<BatchOperationExecutionRecordValue> cancel() {
      writeCancelCommand();
      return expectation.apply(batchOperationKey);
    }

    public Record<ErrorRecordValue> cancelWithError() {
      writeCancelCommand();
      return ERROR_EXPECTATION.apply(batchOperationKey);
    }

    private void writeCancelCommand() {
      if (partition == DEFAULT_PARTITION) {
        partition =
            RecordingExporter.processInstanceRecords()
                .withProcessInstanceKey(batchOperationKey)
                .getFirst()
                .getPartitionId();
      }

      writer.writeCommandOnPartition(
          partition,
          batchOperationKey,
          ProcessInstanceIntent.CANCEL,
          new BatchOperationExecutionRecord().setBatchOperationKey(batchOperationKey));
    }
  }
}
