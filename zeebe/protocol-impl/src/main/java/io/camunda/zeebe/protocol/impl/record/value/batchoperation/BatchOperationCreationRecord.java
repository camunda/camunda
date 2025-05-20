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
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
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
  public static final String PROP_PARTITION_IDS = "partitionIds";

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
  private final ArrayProperty<IntegerValue> partitionIdsProp =
      new ArrayProperty<>(PROP_PARTITION_IDS, IntegerValue::new);

  public BatchOperationCreationRecord() {
    super(6);
    declareProperty(batchOperationKeyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(entityFilterProp)
        .declareProperty(migrationPlanProp)
        .declareProperty(modificationPlanProp)
        .declareProperty(partitionIdsProp);
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

  public DirectBuffer getEntityFilterBuffer() {
    return entityFilterProp.getValue();
  }

  public BatchOperationCreationRecord wrap(final BatchOperationCreationRecord record) {
    batchOperationKeyProp.setValue(record.getBatchOperationKey());
    batchOperationTypeProp.setValue(record.getBatchOperationType());
    entityFilterProp.setValue(record.getEntityFilterBuffer());
    migrationPlanProp.getValue().wrap(record.getMigrationPlan());
    modificationPlanProp.getValue().wrap(record.getModificationPlan());

    partitionIdsProp.reset();
    record.getPartitionIds().forEach(partitionIdsProp::add);
    return this;
  }
}
