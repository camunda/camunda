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
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionRecordValue;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class HistoryDeletionRecord extends UnifiedRecordValue
    implements HistoryDeletionRecordValue {

  private static final StringValue RESOURCE_KEY = new StringValue("resourceKey");
  private static final StringValue RESOURCE_TYPE = new StringValue("resourceType");
  private static final StringValue PROCESS_ID = new StringValue("processId");
  private static final StringValue TENANT_ID = new StringValue("tenantId");
  private static final StringValue DECISION_DEFINITION_ID = new StringValue("decisionDefinitionId");

  private final LongProperty resourceKeyProp = new LongProperty(RESOURCE_KEY);
  private final EnumProperty<HistoryDeletionType> resourceTypeProp =
      new EnumProperty<>(RESOURCE_TYPE, HistoryDeletionType.class);
  private final StringProperty processIdProp = new StringProperty(PROCESS_ID, "");
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty decisionDefinitionIdProp =
      new StringProperty(DECISION_DEFINITION_ID, "");

  public HistoryDeletionRecord() {
    super(5);
    declareProperty(resourceKeyProp)
        .declareProperty(resourceTypeProp)
        .declareProperty(processIdProp)
        .declareProperty(tenantIdProp)
        .declareProperty(decisionDefinitionIdProp);
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
  public String getProcessId() {
    return BufferUtil.bufferAsString(processIdProp.getValue());
  }

  public HistoryDeletionRecord setProcessId(final String processId) {
    processIdProp.setValue(processId);
    return this;
  }

  @Override
  public String getDecisionDefinitionId() {
    return BufferUtil.bufferAsString(decisionDefinitionIdProp.getValue());
  }

  public HistoryDeletionRecord setDecisionDefinitionId(final String decisionDefinitionId) {
    decisionDefinitionIdProp.setValue(decisionDefinitionId);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public HistoryDeletionRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
