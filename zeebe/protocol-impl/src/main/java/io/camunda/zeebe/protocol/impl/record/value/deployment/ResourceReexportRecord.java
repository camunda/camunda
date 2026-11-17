/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.deployment.ResourceReexportRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class ResourceReexportRecord extends UnifiedRecordValue
    implements ResourceReexportRecordValue {
  private final LongProperty resourceKeyProp = new LongProperty("resourceKey", -1L);
  private final StringProperty tenantIdProp = new StringProperty("tenantId", "");

  public ResourceReexportRecord() {
    super(2);
    declareProperty(resourceKeyProp).declareProperty(tenantIdProp);
  }

  @Override
  public long getResourceKey() {
    return resourceKeyProp.getValue();
  }

  public ResourceReexportRecord setResourceKey(final long resourceKey) {
    resourceKeyProp.setValue(resourceKey);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ResourceReexportRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
