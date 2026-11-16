/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.history;

import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;

/**
 * Frozen copy of the hand-written UnpackedObject implementation, kept to verify that the generated
 * codec of the current class keeps the stored format. Do not modify.
 */
public final class HistoryDeletionRecordV1 extends UnifiedRecordValue
    implements HistoryDeletionRecordValue {

  private static final StringValue RESOURCE_KEY_KEY = new StringValue("resourceKey");
  private static final StringValue RESOURCE_TYPE_KEY = new StringValue("resourceType");
  private static final StringValue RESOURCE_STORAGE_ORDINAL_KEY =
      new StringValue("resourceStorageOrdinal");
  private static final StringValue PROCESS_ID_KEY = new StringValue("processId");
  private static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  private static final StringValue DECISION_DEFINITION_ID_KEY =
      new StringValue("decisionDefinitionId");

  private final LongProperty resourceKeyProp = new LongProperty(RESOURCE_KEY_KEY);
  private final EnumProperty<HistoryDeletionType> resourceTypeProp =
      new EnumProperty<>(RESOURCE_TYPE_KEY, HistoryDeletionType.class);
  private final IntegerProperty resourceStorageOrdinalProp =
      new IntegerProperty(RESOURCE_STORAGE_ORDINAL_KEY, 0);
  private final StringProperty processIdProp = new StringProperty(PROCESS_ID_KEY, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty decisionDefinitionIdProp =
      new StringProperty(DECISION_DEFINITION_ID_KEY, "");

  public HistoryDeletionRecordV1() {
    super(6);
    declareProperty(resourceKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(resourceStorageOrdinalProp)
        .declareProperty(processIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(decisionDefinitionIdProp);
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public HistoryDeletionRecordV1 setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public HistoryDeletionType getResourceType() {
    return resourceTypeProp.getValue();
  }

  public HistoryDeletionRecordV1 setResourceType(final HistoryDeletionType resourceType) {
    resourceTypeProp.setValue(resourceType);
    return this;
  }

  @Override
  public int getResourceStorageOrdinal() {
    return resourceStorageOrdinalProp.getValue();
  }

  @Override
  public String getProcessId() {
    return BufferUtil.bufferAsString(processIdProp.getValue());
  }

  public HistoryDeletionRecordV1 setProcessId(final String processId) {
    processIdProp.setValue(processId);
    return this;
  }

  @Override
  public String getDecisionDefinitionId() {
    return BufferUtil.bufferAsString(decisionDefinitionIdProp.getValue());
  }

  public HistoryDeletionRecordV1 setDecisionDefinitionId(final String decisionDefinitionId) {
    decisionDefinitionIdProp.setValue(decisionDefinitionId);
    return this;
  }

  public HistoryDeletionRecordV1 setResourceStorageOrdinal(final int resourceStorageOrdinal) {
    resourceStorageOrdinalProp.setValue(resourceStorageOrdinal);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public HistoryDeletionRecordV1 setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
