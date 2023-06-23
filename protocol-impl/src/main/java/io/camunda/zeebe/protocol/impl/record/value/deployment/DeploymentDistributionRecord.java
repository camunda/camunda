/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.deployment;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.DeploymentDistributionRecordValue;

/**
 * DeploymentDistributionRecord is deprecated as of 8.2.0. A generalised way of distributing
 * commands has been introduced in this version. Distribution should now be handled using the {@link
 * io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord} This record
 * only remains to stay backwards compatible.
 */
@Deprecated
public class DeploymentDistributionRecord extends UnifiedRecordValue
    implements DeploymentDistributionRecordValue {

  private final IntegerProperty partitionIdProperty = new IntegerProperty("partitionId");

  public DeploymentDistributionRecord() {
    declareProperty(partitionIdProperty);
  }

  @Override
  public int getPartitionId() {
    return partitionIdProperty.getValue();
  }

  public void setPartition(final int partitionId) {
    partitionIdProperty.setValue(partitionId);
  }
}
