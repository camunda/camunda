/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;

public class ScaleRecord extends UnifiedRecordValue implements ScaleRecordValue {
  private final IntegerProperty desiredPartitionCountProp =
      new IntegerProperty("desiredPartitionCount", -1);

  // partitions that have been activated because of this scale up operation
  // activated = completed redistribution
  private final ArrayProperty<IntegerValue> redistributedPartitions =
      new ArrayProperty<>("activePartitions", IntegerValue::new);
  // partitions that are ready to take part of processing completely:
  // ready = completed relocation
  private final ArrayProperty<IntegerValue> readyPartitions =
      new ArrayProperty<>("readyPartitions", IntegerValue::new);

  public ScaleRecord() {
    super(3);
    declareProperty(desiredPartitionCountProp)
        .declareProperty(redistributedPartitions)
        .declareProperty(readyPartitions);
  }

  @Override
  public int getDesiredPartitionCount() {
    return desiredPartitionCountProp.getValue();
  }

  public ScaleRecord setDesiredPartitionCount(final int desiredPartitionCount) {
    desiredPartitionCountProp.setValue(desiredPartitionCount);
    return this;
  }

  public ArrayProperty<IntegerValue> getRedistributedPartitions() {
    return redistributedPartitions;
  }

  public ArrayProperty<IntegerValue> getReadyPartitions() {
    return readyPartitions;
  }
}
