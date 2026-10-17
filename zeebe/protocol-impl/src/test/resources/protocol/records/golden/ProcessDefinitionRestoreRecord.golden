/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ProcessDefinitionRestoreRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ProcessDefinitionRestoreRecord extends UnifiedRecordValue
    implements ProcessDefinitionRestoreRecordValue {
  private final LongProperty processDefinitionKeyProp =
      new LongProperty("processDefinitionKey", -1L);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");
  private final IntegerProperty restoredCountProp = new IntegerProperty("restoredCount", 0);

  public ProcessDefinitionRestoreRecord() {
    super(3);
    declareProperty(processDefinitionKeyProp)
        .declareProperty(tenantIdProp)
        .declareProperty(restoredCountProp);
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProp.getValue();
  }

  public ProcessDefinitionRestoreRecord setProcessDefinitionKey(final long processDefinitionKey) {
    processDefinitionKeyProp.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessDefinitionRestoreRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  @Override
  public int getRestoredCount() {
    return restoredCountProp.getValue();
  }

  public ProcessDefinitionRestoreRecord setRestoredCount(final int restoredCount) {
    restoredCountProp.setValue(restoredCount);
    return this;
  }
}
