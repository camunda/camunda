/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.impl.record.value.NestedRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import java.util.Collection;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BatchOperationCreationRecord extends UnifiedRecordValue
    implements BatchOperationCreationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_BATCH_OPERATION_TYPE = "batchOperationType";
  public static final String PROP_ENTITY_FILTER = "entityFilter";
  public static final String PROP_MIGRATION_PLAN = "migrationPlan";
  public static final String PROP_MODIFICATION_PLAN = "modificationPlan";
  public static final String PROP_AUTHENTICATION = "authentication";
  public static final String PROP_AUTHORIZATION_CHECK = "authorizationCheck";
  public static final String PROP_PARTITION_IDS = "partitionIds";
  public static final String PROP_FOLLOWUP_COMMAND = "followUpCommand";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY, -1);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(PROP_BATCH_OPERATION_TYPE, BatchOperationType.class);
  private final BinaryProperty entityFilterProp =
      new BinaryProperty(PROP_ENTITY_FILTER, new UnsafeBuffer());
  private final ObjectProperty<BatchOperationProcessInstanceMigrationPlan> migrationPlanProp =
      new ObjectProperty<>(PROP_MIGRATION_PLAN, new BatchOperationProcessInstanceMigrationPlan());
  private final ObjectProperty<BatchOperationProcessInstanceModificationPlan> modificationPlanProp =
      new ObjectProperty<>(
          PROP_MODIFICATION_PLAN, new BatchOperationProcessInstanceModificationPlan());
  // Authentication, needed for query in scheduler + command auth
  private final DocumentProperty authenticationProp = new DocumentProperty(PROP_AUTHENTICATION);
  // Authorization check, needed for single operations that skip batch permission checks
  private final DocumentProperty authorizationCheckProp =
      new DocumentProperty(PROP_AUTHORIZATION_CHECK);
  private final ArrayProperty<IntegerValue> partitionIdsProp =
      new ArrayProperty<>(PROP_PARTITION_IDS, IntegerValue::new);
  private final ObjectProperty<NestedRecord> followUpCommandProp =
      new ObjectProperty<>(PROP_FOLLOWUP_COMMAND, new NestedRecord());

  public BatchOperationCreationRecord() {
    super(9);
    declareProperty(batchOperationKeyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(entityFilterProp)
        .declareProperty(migrationPlanProp)
        .declareProperty(modificationPlanProp)
        .declareProperty(authenticationProp)
        .declareProperty(authorizationCheckProp)
        .declareProperty(partitionIdsProp)
        .declareProperty(followUpCommandProp);
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public BatchOperationCreationRecord setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public BatchOperationCreationRecord setBatchOperationType(
      final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  @Override
  public String getEntityFilter() {
    return entityFilterProp.getValue().capacity() == 0
        ? null
        : MsgPackConverter.convertToJson(entityFilterProp.getValue());
  }

  public BatchOperationCreationRecord setEntityFilter(final DirectBuffer filterBuffer) {
    entityFilterProp.setValue(new UnsafeBuffer(filterBuffer));
    return this;
  }

  @Override
  public BatchOperationProcessInstanceMigrationPlanValue getMigrationPlan() {
    return migrationPlanProp.getValue();
  }

  public BatchOperationCreationRecord setMigrationPlan(
      final BatchOperationProcessInstanceMigrationPlanValue migrationPlan) {
    migrationPlanProp.getValue().wrap(migrationPlan);
    return this;
  }

  @Override
  public BatchOperationProcessInstanceModificationPlanValue getModificationPlan() {
    return modificationPlanProp.getValue();
  }

  public BatchOperationCreationRecord setModificationPlan(
      final BatchOperationProcessInstanceModificationPlanValue migrationPlan) {
    modificationPlanProp.getValue().wrap(migrationPlan);
    return this;
  }

  @Override
  public List<Integer> getPartitionIds() {
    return partitionIdsProp.stream().map(IntegerValue::getValue).toList();
  }

  public BatchOperationCreationRecord setPartitionIds(final Collection<Integer> partitionIds) {
    partitionIdsProp.reset();
    for (final Integer partitionId : partitionIds) {
      partitionIdsProp.add().setValue(partitionId);
    }

    return this;
  }

  @Override
  public NestedRecord getFollowUpCommand() {
    return followUpCommandProp.getValue();
  }

  public DirectBuffer getAuthenticationBuffer() {
    return authenticationProp.getValue();
  }

  public BatchOperationCreationRecord setAuthentication(final DirectBuffer authentication) {
    authenticationProp.setValue(authentication);
    return this;
  }

  public DirectBuffer getAuthorizationCheckBuffer() {
    return authorizationCheckProp.getValue();
  }

  public BatchOperationCreationRecord setAuthorizationCheck(final DirectBuffer authorizationCheck) {
    authorizationCheckProp.setValue(authorizationCheck);
    return this;
  }

  public DirectBuffer getEntityFilterBuffer() {
    return entityFilterProp.getValue();
  }

  public BatchOperationCreationRecord setFollowUpCommand(
      final ValueType valueType, final Intent intent, final UnifiedRecordValue value) {
    getFollowUpCommand().setValueType(valueType).setIntent(intent).setCommandValue(value);
    return this;
  }

  public BatchOperationCreationRecord wrap(final BatchOperationCreationRecord record) {
    batchOperationKeyProp.setValue(record.getBatchOperationKey());
    batchOperationTypeProp.setValue(record.getBatchOperationType());
    entityFilterProp.setValue(record.getEntityFilterBuffer());
    migrationPlanProp.getValue().wrap(record.getMigrationPlan());
    modificationPlanProp.getValue().wrap(record.getModificationPlan());
    authenticationProp.setValue(record.getAuthenticationBuffer());
    setPartitionIds(record.getPartitionIds());
    followUpCommandProp.getValue().wrap(record.getFollowUpCommand());

    return this;
  }
}
