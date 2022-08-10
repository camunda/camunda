/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

public class DeploymentDistributionRecord extends UnifiedRecordValue
    implements DeploymentDistributionRecordValue {

  private final IntegerProperty partitionIdProperty = new IntegerProperty("partitionId");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public DeploymentDistributionRecord() {
    declareProperty(partitionIdProperty).declareProperty(tenantIdProp);
  }

  @Override
  public int getPartitionId() {
    return partitionIdProperty.getValue();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public DeploymentDistributionRecord setTenantId(final DirectBuffer tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  public DeploymentDistributionRecord setPartition(final int partitionId) {
    partitionIdProperty.setValue(partitionId);
    return this;
  }
}
