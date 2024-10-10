/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.tenant;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;

public class PersistedTenant extends UnpackedObject implements DbValue {

  private final ObjectProperty<TenantRecord> tenantRecordProp =
      new ObjectProperty<>("tenantRecord", new TenantRecord());

  public PersistedTenant() {
    super(1);
    declareProperty(tenantRecordProp);
  }

  /**
   * Gets the TenantRecord stored in this persisted object.
   *
   * @return the TenantRecord
   */
  public TenantRecord getTenant() {
    return tenantRecordProp.getValue();
  }

  /**
   * Sets the TenantRecord for this persisted object.
   *
   * @param record the TenantRecord to set
   */
  public void setTenant(final TenantRecord record) {
    tenantRecordProp.getValue().wrap(record);
  }
}
