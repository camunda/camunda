/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.NestedRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue.BatchOperationJobUpdatePlanValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.collections.IntArrayList;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class BatchOperationCreationRecord
    extends MsgpackBufferValue<BatchOperationCreationRecord>
    implements MsgpackRecordValue, BatchOperationCreationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_STORAGE_ORDINAL = "storageOrdinal";
  public static final String PROP_BATCH_OPERATION_TYPE = "batchOperationType";
  public static final String PROP_ENTITY_FILTER = "entityFilter";
  public static final String PROP_MIGRATION_PLAN = "migrationPlan";
  public static final String PROP_MODIFICATION_PLAN = "modificationPlan";
  public static final String PROP_JOB_UPDATE_PLAN = "jobUpdatePlan";
  public static final String PROP_AUTHENTICATION = "authentication";
  public static final String PROP_AUTHORIZATION_CHECK = "authorizationCheck";
  public static final String PROP_PARTITION_IDS = "partitionIds";
  public static final String PROP_FOLLOWUP_COMMAND = "followUpCommand";

  long batchOperationKey = -1L;
  int storageOrdinal = 0;
  @Nullable BatchOperationType batchOperationType;

  @MsgpackProperty(binary = true)
  DirectBuffer entityFilter = Decoding.EMPTY;

  final BatchOperationProcessInstanceMigrationPlan migrationPlan =
      new BatchOperationProcessInstanceMigrationPlan();
  final BatchOperationProcessInstanceModificationPlan modificationPlan =
      new BatchOperationProcessInstanceModificationPlan();
  final BatchOperationJobUpdatePlan jobUpdatePlan = new BatchOperationJobUpdatePlan();

  // Authentication, needed for query in scheduler + command auth
  @MsgpackProperty(document = true)
  DirectBuffer authentication = DocumentValue.EMPTY_DOCUMENT;

  // Authorization check, needed for single operations that skip batch permission checks
  @MsgpackProperty(document = true)
  DirectBuffer authorizationCheck = DocumentValue.EMPTY_DOCUMENT;

  final IntArrayList partitionIds = new IntArrayList();
  final NestedRecord followUpCommand = new NestedRecord();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getBatchOperationKey() {
    return batchOperationKey;
  }

  public BatchOperationCreationRecord setBatchOperationKey(final Long batchOperationKey) {
    this.batchOperationKey = batchOperationKey;
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public BatchOperationCreationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return Decoding.required(batchOperationType, "batchOperationType");
  }

  public BatchOperationCreationRecord setBatchOperationType(
      final BatchOperationType batchOperationType) {
    this.batchOperationType = batchOperationType;
    return this;
  }

  @Override
  @SuppressWarnings("NullAway") // the value interface is not @Nullable, but an empty filter is null
  public String getEntityFilter() {
    return entityFilter.capacity() == 0 ? null : MsgPackConverter.convertToJson(entityFilter);
  }

  public BatchOperationCreationRecord setEntityFilter(final DirectBuffer filterBuffer) {
    entityFilter = new UnsafeBuffer(filterBuffer);
    return this;
  }

  @Override
  public BatchOperationProcessInstanceMigrationPlanValue getMigrationPlan() {
    return migrationPlan;
  }

  public BatchOperationCreationRecord setMigrationPlan(
      final BatchOperationProcessInstanceMigrationPlanValue migrationPlan) {
    this.migrationPlan.wrap(migrationPlan);
    return this;
  }

  @Override
  public BatchOperationProcessInstanceModificationPlanValue getModificationPlan() {
    return modificationPlan;
  }

  public BatchOperationCreationRecord setModificationPlan(
      final BatchOperationProcessInstanceModificationPlanValue migrationPlan) {
    modificationPlan.wrap(migrationPlan);
    return this;
  }

  @Override
  public BatchOperationJobUpdatePlanValue getJobUpdatePlan() {
    return jobUpdatePlan;
  }

  public BatchOperationCreationRecord setJobUpdatePlan(
      final BatchOperationJobUpdatePlanValue jobUpdatePlan) {
    this.jobUpdatePlan.wrap(jobUpdatePlan);
    return this;
  }

  @Override
  public List<Integer> getPartitionIds() {
    return partitionIds.stream().toList();
  }

  public BatchOperationCreationRecord setPartitionIds(final Collection<Integer> partitionIds) {
    this.partitionIds.clear();
    for (final Integer partitionId : partitionIds) {
      this.partitionIds.addInt(partitionId);
    }

    return this;
  }

  @Override
  public NestedRecord getFollowUpCommand() {
    return followUpCommand;
  }

  public DirectBuffer getAuthenticationBuffer() {
    return authentication;
  }

  public BatchOperationCreationRecord setAuthentication(final DirectBuffer authentication) {
    this.authentication = Decoding.document(authentication);
    return this;
  }

  public DirectBuffer getAuthorizationCheckBuffer() {
    return authorizationCheck;
  }

  public BatchOperationCreationRecord setAuthorizationCheck(final DirectBuffer authorizationCheck) {
    this.authorizationCheck = Decoding.document(authorizationCheck);
    return this;
  }

  public DirectBuffer getEntityFilterBuffer() {
    return entityFilter;
  }

  public BatchOperationCreationRecord setFollowUpCommand(
      final ValueType valueType, final Intent intent, final MsgpackRecordValue value) {
    getFollowUpCommand().setValueType(valueType).setIntent(intent).setRecordValue(value);
    return this;
  }

  public BatchOperationCreationRecord wrap(final BatchOperationCreationRecord record) {
    batchOperationKey = record.getBatchOperationKey();
    storageOrdinal = record.getStorageOrdinal();
    batchOperationType = record.getBatchOperationType();
    entityFilter = record.getEntityFilterBuffer();
    migrationPlan.wrap(record.getMigrationPlan());
    modificationPlan.wrap(record.getModificationPlan());
    jobUpdatePlan.wrap(record.getJobUpdatePlan());
    authentication = Decoding.document(record.getAuthenticationBuffer());
    setPartitionIds(record.getPartitionIds());
    followUpCommand.wrap(record.getFollowUpCommand());

    return this;
  }

  @Override
  protected MsgpackCodec<BatchOperationCreationRecord> codec() {
    return BatchOperationCreationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BatchOperationCreationRecord> ops() {
    return BatchOperationCreationRecordOps.INSTANCE;
  }
}
