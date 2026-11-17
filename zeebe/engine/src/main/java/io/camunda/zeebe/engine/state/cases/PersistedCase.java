/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.cases;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.jspecify.annotations.NullMarked;

/**
 * A case, stored under the key of its root process instance. The process instance key is not part
 * of the value, as it is the key.
 */
@NullMarked
public final class PersistedCase extends UnpackedObject implements DbValue {

  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty bpmnProcessIdProp = new StringProperty("bpmnProcessId", "");
  private final StringProperty businessIdProp = new StringProperty("businessId", "");
  private final StringProperty titleProp = new StringProperty("title", "");

  public PersistedCase() {
    super(4);
    declareProperty(tenantIdProp)
        .declareProperty(bpmnProcessIdProp)
        .declareProperty(businessIdProp)
        .declareProperty(titleProp);
  }

  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public PersistedCase setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProp.getValue());
  }

  public PersistedCase setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProp.setValue(bpmnProcessId);
    return this;
  }

  public String getBusinessId() {
    return BufferUtil.bufferAsString(businessIdProp.getValue());
  }

  public PersistedCase setBusinessId(final String businessId) {
    businessIdProp.setValue(businessId);
    return this;
  }

  public String getTitle() {
    return BufferUtil.bufferAsString(titleProp.getValue());
  }

  public PersistedCase setTitle(final String title) {
    titleProp.setValue(title);
    return this;
  }
}
