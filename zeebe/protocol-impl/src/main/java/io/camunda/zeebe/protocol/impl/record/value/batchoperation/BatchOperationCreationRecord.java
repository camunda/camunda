/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationCreationRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class BatchOperationCreationRecord extends UnifiedRecordValue
    implements BatchOperationCreationRecordValue {

  public static final String PROP_BATCH_OPERATION_KEY = "batchOperationKey";
  public static final String PROP_BATCH_OPERATION_TYPE = "batchOperationType";
  public static final String PROP_ENTITY_FILTER = "entityFilter";

  private final LongProperty batchOperationKeyProp = new LongProperty(PROP_BATCH_OPERATION_KEY, -1);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          PROP_BATCH_OPERATION_TYPE, BatchOperationType.class, BatchOperationType.UNSPECIFIED);
  private final BinaryProperty entityFilterProp =
      new BinaryProperty(PROP_ENTITY_FILTER, new UnsafeBuffer());

  public BatchOperationCreationRecord() {
    super(3);
    declareProperty(batchOperationKeyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(entityFilterProp);
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

  public DirectBuffer getEntityFilterBuffer() {
    return entityFilterProp.getValue();
  }
}
