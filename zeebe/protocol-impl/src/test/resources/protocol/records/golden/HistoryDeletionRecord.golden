/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.history;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;

public class HistoryDeletionRecord extends UnifiedRecordValue
    implements HistoryDeletionRecordValue {

  private static final StringValue RESOURCE_KEY = new StringValue("resourceKey");
  private static final StringValue RESOURCE_TYPE = new StringValue("resourceType");
  private static final StringValue BATCH_OPERATION_KEY = new StringValue("batchOperationKey");

  private final LongProperty resourceKeyProp = new LongProperty(RESOURCE_KEY);
  private final EnumProperty<HistoryDeletionType> resourceTypeProp =
      new EnumProperty<>(RESOURCE_TYPE, HistoryDeletionType.class);
  private final LongProperty batchOperationKeyProp = new LongProperty(BATCH_OPERATION_KEY);

  public HistoryDeletionRecord() {
    super(3);
    declareProperty(resourceKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(batchOperationKeyProp);
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public HistoryDeletionRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public HistoryDeletionType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public HistoryDeletionRecord setResourceType(final HistoryDeletionType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public HistoryDeletionRecord setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }
}
