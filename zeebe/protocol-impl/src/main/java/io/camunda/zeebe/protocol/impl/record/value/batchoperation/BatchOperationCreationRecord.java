/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.batchoperation;

import io.camunda.search.filter.FilterBase;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
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

  private final LongProperty batchOperationKeyProp =
      new LongProperty(PROP_BATCH_OPERATION_KEY, -1);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          PROP_BATCH_OPERATION_TYPE, BatchOperationType.class, BatchOperationType.UNSPECIFIED);
  private final DocumentProperty filterProp = new DocumentProperty("filter");

  public BatchOperationCreationRecord() {
    super(3);
    declareProperty(batchOperationKeyProp)
        .declareProperty(batchOperationTypeProp).declareProperty(filterProp);
  }

  @Override
  public Long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public void setBatchOperationKey(final Long batchOperationKey) {
    batchOperationKeyProp.reset();
    batchOperationKeyProp.setValue(batchOperationKey);
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
  public DirectBuffer getFilterBuffer() {
    return filterProp.getValue();
  }

  public BatchOperationCreationRecord setFilter(final DirectBuffer filterBuffer) {
    filterProp.setValue(new UnsafeBuffer(filterBuffer));
    return this;
  }

  public BatchOperationCreationRecord setFilter(final FilterBase filter) {
    setFilter(new UnsafeBuffer(MsgPackConverter.convertToMsgPack(filter)));
    return this;
  }

  public void wrap(final BatchOperationCreationRecord record) {
    setBatchOperationKey(record.getBatchOperationKey());
    setBatchOperationType(record.getBatchOperationType());
    setFilter(record.getFilterBuffer());
  }
}
