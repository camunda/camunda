/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.resource;

import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class ResourceDeletionRecord extends UnifiedRecordValue
    implements ResourceDeletionRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue RESOURCE_KEY_KEY = new StringValue("resourceKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue DELETE_HISTORY_KEY = new StringValue("deleteHistory");
  private static final StringValue BATCH_OPERATION_KEY = new StringValue("batchOperationKey");
  private static final StringValue BATCH_OPERATION_TYPE = new StringValue("batchOperationType");
  private static final StringValue RESOURCE_TYPE = new StringValue("resourceType");
  private static final StringValue RESOURCE_ID = new StringValue("resourceId");

  private final LongProperty resourceKeyProp = new LongProperty(RESOURCE_KEY_KEY);
  private final StringProperty tenantIdProp = new StringProperty(TENANT_ID_KEY, "");
  private final BooleanProperty deleteHistoryProp = new BooleanProperty(DELETE_HISTORY_KEY, false);
  private final LongProperty batchOperationKeyProp = new LongProperty(BATCH_OPERATION_KEY, -1);
  private final EnumProperty<BatchOperationType> batchOperationTypeProp =
      new EnumProperty<>(
          BATCH_OPERATION_TYPE,
          BatchOperationType.class,
          BatchOperationType.DELETE_PROCESS_INSTANCE);
  private final EnumProperty<ResourceType> resourceTypeProp =
      new EnumProperty<>(RESOURCE_TYPE, ResourceType.class, ResourceType.UNKNOWN);
  private final StringProperty resourceIdProp = new StringProperty(RESOURCE_ID, "");

  public ResourceDeletionRecord() {
    super(7);
    declareProperty(resourceKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deleteHistoryProp)
        .declareProperty(batchOperationKeyProp)
        .declareProperty(batchOperationTypeProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(resourceIdProp);
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public ResourceDeletionRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public boolean isDeleteHistory() {
    return deleteHistoryProp.getValue();
  }

  public ResourceDeletionRecord setDeleteHistory(final boolean deleteHistory) {
    deleteHistoryProp.setValue(deleteHistory);
    return this;
  }

  @Override
  public BatchOperationType getBatchOperationType() {
    return batchOperationTypeProp.getValue();
  }

  public ResourceDeletionRecord setBatchOperationType(final BatchOperationType batchOperationType) {
    batchOperationTypeProp.setValue(batchOperationType);
    return this;
  }

  @Override
  public ResourceType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public ResourceDeletionRecord setResourceType(final ResourceType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  @Override
  public String getResourceId() {
    return BufferUtil.bufferAsString(resourceIdProp.getValue());
  }

  public ResourceDeletionRecord setResourceId(final String resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  public ResourceDeletionRecord setResourceId(final DirectBuffer resourceId) {
    resourceIdProp.setValue(resourceId);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ResourceDeletionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public long getBatchOperationKey() {
    return batchOperationKeyProp.getValue();
  }

  public ResourceDeletionRecord setBatchOperationKey(final long batchOperationKey) {
    batchOperationKeyProp.setValue(batchOperationKey);
    return this;
  }
}
