/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.util.client;

import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.util.AuthorizationUtil;
import io.camunda.zeebe.protocol.impl.encoding.AuthInfo;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationExecutionRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationLifecycleManagementRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationPartitionLifecycleRecord;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationProcessInstanceModificationPlan;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.intent.BatchOperationExecutionIntent;
import io.camunda.zeebe.protocol.record.intent.BatchOperationIntent;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationProcessInstanceMigrationPlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationExecutionRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationLifecycleManagementRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationPartitionLifecycleRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import java.util.Random;
import java.util.function.Function;
import org.agrona.DirectBuffer;

public final class BatchOperationClient {

  public static final int DEFAULT_PARTITION = 1;

  private final CommandWriter writer;

  public BatchOperationClient(final CommandWriter writer) {
    this.writer = writer;
  }

  public BatchOperationCreationClient newCreation(final BatchOperationType type) {
    return new BatchOperationCreationClient(writer, type);
  }

  public BatchOperationExecutionClient newExecution() {
    return new BatchOperationExecutionClient(writer);
  }

  public BatchOperationLifecycleClient newLifecycle() {
    return new BatchOperationLifecycleClient(writer);
  }

  public BatchOperationPartitionLifecycleClient newPartitionLifecycle() {
    return new BatchOperationPartitionLifecycleClient(writer);
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

    private final CommandWriter writer;
    private final BatchOperationCreationRecord batchOperationCreationRecord;
    private int partition = DEFAULT_PARTITION;

    private Function<Long, Record<BatchOperationCreationRecordValue>> expectation =
        SUCCESS_EXPECTATION;

    private boolean waitForInitialized = false;

    public BatchOperationCreationClient(final CommandWriter writer, final BatchOperationType type) {
      this.writer = writer;
      batchOperationCreationRecord = new BatchOperationCreationRecord();
      batchOperationCreationRecord.setBatchOperationType(type);
    }

    public BatchOperationCreationClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public BatchOperationCreationClient withFilter(final DirectBuffer filter) {
      batchOperationCreationRecord.setEntityFilter(filter);
      return this;
    }

    public BatchOperationCreationClient withAuthentication(final DirectBuffer authentication) {
      if (authentication != null) {
        batchOperationCreationRecord.setAuthentication(authentication);
      }
      return this;
    }

    public BatchOperationCreationClient withMigrationPlan(
        final BatchOperationProcessInstanceMigrationPlanValue migrationPlan) {
      batchOperationCreationRecord.setMigrationPlan(migrationPlan);
      return this;
    }

    public BatchOperationCreationClient withModificationPlan(
        final BatchOperationProcessInstanceModificationPlan modificationPlan) {
      batchOperationCreationRecord.setModificationPlan(modificationPlan);
      return this;
    }

    public BatchOperationCreationClient withBatchOperationKey(final long batchOperationKey) {
      batchOperationCreationRecord.setBatchOperationKey(batchOperationKey);
      return this;
    }

    /**
     * This is needed if we want to make sure that the scheduler does it's work and created chunks
     *
     * @return
     */
    public BatchOperationCreationClient waitForInitialized() {
      waitForInitialized = true;
      return this;
    }

    public BatchOperationCreationWithResultClient withResult() {
      return new BatchOperationCreationWithResultClient(writer, batchOperationCreationRecord);
    }

    public Record<BatchOperationCreationRecordValue> create() {
      return create(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationCreationRecordValue> create(final String username) {
      return create(AuthorizationUtil.getAuthInfo(username));
    }

    public Record<BatchOperationCreationRecordValue> create(final AuthInfo authorizations) {
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

      if (waitForInitialized) {
        RecordingExporter.batchOperationCreationRecords()
            .withIntent(BatchOperationIntent.INITIALIZED)
            .withBatchOperationKey(resultingRecord.getKey())
            .await();
      }

      return resultingRecord;
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

  public static class BatchOperationExecutionClient {

    private final CommandWriter writer;
    private final BatchOperationExecutionRecord batchOperationExecutionRecord;
    private int partition = DEFAULT_PARTITION;

    public BatchOperationExecutionClient(final CommandWriter writer) {
      this.writer = writer;
      batchOperationExecutionRecord = new BatchOperationExecutionRecord();
    }

    public BatchOperationExecutionClient withBatchOperationKey(final long batchOperationKey) {
      batchOperationExecutionRecord.setBatchOperationKey(batchOperationKey);
      return this;
    }

    public BatchOperationExecutionClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public Record<BatchOperationExecutionRecordValue> execute() {
      return execute(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationExecutionRecordValue> execute(final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationExecutionIntent.EXECUTE)
                      .event(batchOperationExecutionRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));
      return RecordingExporter.batchOperationExecutionRecords()
          .withIntent(BatchOperationExecutionIntent.EXECUTED)
          .withSourceRecordPosition(position)
          .withPartitionId(partition)
          .getFirst();
    }

    public void executeWithoutExpectation() {
      executeWithoutExpectation(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public void executeWithoutExpectation(final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationExecutionIntent.EXECUTE)
                      .event(batchOperationExecutionRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));
    }
  }

  public static class BatchOperationLifecycleClient {

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        CANCEL_SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .withIntent(BatchOperationIntent.CANCELED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        CANCEL_REJECTION_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .onlyCommandRejections()
                    .withIntent(BatchOperationIntent.CANCEL)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        SUSPEND_SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .withIntent(BatchOperationIntent.SUSPENDED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        SUSPEND_REJECTION_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .onlyCommandRejections()
                    .withIntent(BatchOperationIntent.SUSPEND)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        RESUME_SUCCESS_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .withIntent(BatchOperationIntent.RESUMED)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private static final Function<Long, Record<BatchOperationLifecycleManagementRecordValue>>
        RESUME_REJECTION_EXPECTATION =
            (position) ->
                RecordingExporter.batchOperationLifecycleRecords()
                    .onlyCommandRejections()
                    .withIntent(BatchOperationIntent.RESUME)
                    .withSourceRecordPosition(position)
                    .getFirst();

    private final CommandWriter writer;
    private final BatchOperationLifecycleManagementRecord batchOperationLifecycleManagementRecord;
    private final int partition = DEFAULT_PARTITION;
    private boolean expectSuccess = true;

    public BatchOperationLifecycleClient(final CommandWriter writer) {
      this.writer = writer;
      batchOperationLifecycleManagementRecord = new BatchOperationLifecycleManagementRecord();
    }

    public BatchOperationLifecycleClient withBatchOperationKey(final long batchOperationKey) {
      batchOperationLifecycleManagementRecord.setBatchOperationKey(batchOperationKey);
      return this;
    }

    public BatchOperationLifecycleClient expectRejection() {
      expectSuccess = false;
      return this;
    }

    public Record<BatchOperationLifecycleManagementRecordValue> cancel() {
      return cancel(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> cancel(final String username) {
      return cancel(AuthorizationUtil.getAuthInfo(username));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> cancel(
        final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationIntent.CANCEL)
                      .event(batchOperationLifecycleManagementRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      return expectSuccess
          ? CANCEL_SUCCESS_EXPECTATION.apply(position)
          : CANCEL_REJECTION_EXPECTATION.apply(position);
    }

    public Record<BatchOperationLifecycleManagementRecordValue> suspend() {
      return suspend(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> suspend(final String username) {
      return suspend(AuthorizationUtil.getAuthInfo(username));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> suspend(
        final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationIntent.SUSPEND)
                      .event(batchOperationLifecycleManagementRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      return expectSuccess
          ? SUSPEND_SUCCESS_EXPECTATION.apply(position)
          : SUSPEND_REJECTION_EXPECTATION.apply(position);
    }

    public void suspendWithoutExpectations() {
      suspendWithoutExpectations(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public void suspendWithoutExpectations(final AuthInfo authorizations) {
      writer.writeCommandOnPartition(
          partition,
          r ->
              r.intent(BatchOperationIntent.SUSPEND)
                  .event(batchOperationLifecycleManagementRecord)
                  .authorizations(authorizations)
                  .requestId(new Random().nextLong())
                  .requestStreamId(new Random().nextInt()));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> resume() {
      return resume(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> resume(final String username) {
      return resume(AuthorizationUtil.getAuthInfo(username));
    }

    public Record<BatchOperationLifecycleManagementRecordValue> resume(
        final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationIntent.RESUME)
                      .event(batchOperationLifecycleManagementRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      return expectSuccess
          ? RESUME_SUCCESS_EXPECTATION.apply(position)
          : RESUME_REJECTION_EXPECTATION.apply(position);
    }

    public void resumeWithoutExpectation() {
      resumeWithoutExpectation(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public void resumeWithoutExpectation(final AuthInfo authorizations) {
      writer.writeCommandOnPartition(
          partition,
          r ->
              r.intent(BatchOperationIntent.RESUME)
                  .event(batchOperationLifecycleManagementRecord)
                  .authorizations(authorizations)
                  .requestId(new Random().nextLong())
                  .requestStreamId(new Random().nextInt()));
    }
  }

  public static class BatchOperationPartitionLifecycleClient {

    private final CommandWriter writer;
    private final BatchOperationPartitionLifecycleRecord batchOperationPartitionLifecycleRecord;
    private int partition = DEFAULT_PARTITION;

    public BatchOperationPartitionLifecycleClient(final CommandWriter writer) {
      this.writer = writer;
      batchOperationPartitionLifecycleRecord = new BatchOperationPartitionLifecycleRecord();
    }

    public BatchOperationPartitionLifecycleClient withBatchOperationKey(
        final long batchOperationKey) {
      batchOperationPartitionLifecycleRecord.setBatchOperationKey(batchOperationKey);
      return this;
    }

    public BatchOperationPartitionLifecycleClient onPartition(final int partition) {
      this.partition = partition;
      return this;
    }

    public Record<BatchOperationPartitionLifecycleRecordValue> fail() {
      return fail(
          AuthorizationUtil.getAuthInfoWithClaim(Authorization.AUTHORIZED_ANONYMOUS_USER, true));
    }

    public Record<BatchOperationPartitionLifecycleRecordValue> fail(final String username) {
      return fail(AuthorizationUtil.getAuthInfo(username));
    }

    public Record<BatchOperationPartitionLifecycleRecordValue> fail(final AuthInfo authorizations) {
      final long position =
          writer.writeCommandOnPartition(
              partition,
              r ->
                  r.intent(BatchOperationIntent.FAIL)
                      .event(batchOperationPartitionLifecycleRecord)
                      .authorizations(authorizations)
                      .requestId(new Random().nextLong())
                      .requestStreamId(new Random().nextInt()));

      return RecordingExporter.batchOperationPartitionLifecycleRecords()
          .withIntent(BatchOperationIntent.PARTITION_FAILED)
          .withSourceRecordPosition(position)
          .withPartitionId(partition)
          .getFirst();
    }
  }
}
