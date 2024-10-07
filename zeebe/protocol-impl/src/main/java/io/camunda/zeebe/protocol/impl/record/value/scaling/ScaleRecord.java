/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;

public class ScaleRecord extends UnifiedRecordValue implements ScaleRecordValue {
  private final IntegerProperty currentPartitionCountProp =
      new IntegerProperty("currentPartitionCount", -1);
  private final IntegerProperty desiredPartitionCountProp =
      new IntegerProperty("desiredPartitionCount", -1);

  public ScaleRecord() {
    super(2);
    declareProperty(currentPartitionCountProp).declareProperty(desiredPartitionCountProp);
  }

  @Override
  public int currentPartitionCount() {
    return currentPartitionCountProp.getValue();
  }

  @Override
  public int desiredPartitionCount() {
    return desiredPartitionCountProp.getValue();
  }

  public ScaleRecord setCurrentPartitionCount(final int currentPartitionCount) {
    currentPartitionCountProp.setValue(currentPartitionCount);
    return this;
  }

  public ScaleRecord setDesiredPartitionCount(final int desiredPartitionCount) {
    desiredPartitionCountProp.setValue(desiredPartitionCount);
    return this;
  }
}
