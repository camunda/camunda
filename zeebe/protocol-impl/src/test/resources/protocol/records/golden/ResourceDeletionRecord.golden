/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.resource;

import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ResourceDeletionRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ResourceDeletionRecord extends UnifiedRecordValue
    implements ResourceDeletionRecordValue {

  // Static StringValue keys to avoid memory waste
  private static final StringValue RESOURCE_KEY_KEY = new StringValue("resourceKey");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue DELETE_HISTORY_KEY = new StringValue("deleteHistory");

  private final LongProperty resourceKeyProp = new LongProperty(RESOURCE_KEY_KEY);
  private final StringProperty tenantIdProp = new StringProperty(TENANT_ID_KEY, "");
  private final BooleanProperty deleteHistoryProp = new BooleanProperty(DELETE_HISTORY_KEY, false);

  public ResourceDeletionRecord() {
    super(3);
    declareProperty(resourceKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(deleteHistoryProp);
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
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ResourceDeletionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
