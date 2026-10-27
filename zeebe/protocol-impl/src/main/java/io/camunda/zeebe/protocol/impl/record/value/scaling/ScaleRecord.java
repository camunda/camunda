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
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

public class ScaleRecord extends UnifiedRecordValue implements ScaleRecordValue {
  private final IntegerProperty desiredPartitionCountProp =
      new IntegerProperty("desiredPartitionCount", -1);

  // partitions that have been activated because of this scale up operation
  // activated = completed redistribution
  private final ArrayProperty<IntegerValue> redistributedPartitions =
      new ArrayProperty<>("redistributedPartitions", IntegerValue::new);
  // partitions that are ready to take part of processing completely:
  // ready = completed relocation
  private final ArrayProperty<IntegerValue> relocatedPartitions =
      new ArrayProperty<>("relocatedPartitions", IntegerValue::new);

  private final LongProperty scalingPosition = new LongProperty("scalingPosition", -1L);

  /**
   * Represent the number of partitions that participate in message correlation. The set of
   * partitions is in the range [1, messageCorrelationPartitions]
   */
  private final IntegerProperty messageCorrelationPartitions =
      new IntegerProperty("messageCorrelationPartitions", -1);

  public ScaleRecord() {
    super(5);
    declareProperty(desiredPartitionCountProp)
        .declareProperty(redistributedPartitions)
        .declareProperty(relocatedPartitions)
        .declareProperty(messageCorrelationPartitions)
        .declareProperty(scalingPosition);
  }

  @Override
  public int getDesiredPartitionCount() {
    return desiredPartitionCountProp.getValue();
  }

  public ScaleRecord setDesiredPartitionCount(final int desiredPartitionCount) {
    desiredPartitionCountProp.setValue(desiredPartitionCount);
    return this;
  }

  @Override
  public SortedSet<Integer> getRedistributedPartitions() {
    return redistributedPartitions.stream()
        .map(IntegerValue::getValue)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public Collection<Integer> getRelocatedPartitions() {
    return relocatedPartitions.stream().map(IntegerValue::getValue).toList();
  }

  public ScaleRecord setRelocatedPartitions(final Collection<Integer> partitions) {
    relocatedPartitions.reset();
    for (final int partition : partitions) {
      relocatedPartitions.add().setValue(partition);
    }
    return this;
  }

  @Override
  public int getMessageCorrelationPartitions() {
    return messageCorrelationPartitions.getValue();
  }

  @Override
  public long getScalingPosition() {
    return scalingPosition.getValue();
  }

  public ScaleRecord setScalingPosition(final long position) {
    scalingPosition.setValue(position);
    return this;
  }

  public ScaleRecord setMessageCorrelationPartitions(final int messageCorrelationPartitions) {
    this.messageCorrelationPartitions.setValue(messageCorrelationPartitions);
    return this;
  }

  public ScaleRecord setRedistributedPartitions(final Collection<Integer> partitions) {
    redistributedPartitions.reset();
    for (final int partition : partitions) {
      redistributedPartitions.add().setValue(partition);
    }
    return this;
  }

  //// Helpers to fill the required fields depending on the intent
  public ScaleRecord scaleUp(final int desiredPartitionCount) {
    setDesiredPartitionCount(desiredPartitionCount);
    return this;
  }

  public ScaleRecord status() {
    return this;
  }

  public ScaleRecord statusResponse(
      final int desiredPartitionCount,
      final Collection<Integer> redistributedPartitions,
      final int messageCorrelationPartitions,
      final long scalingPosition) {
    setDesiredPartitionCount(desiredPartitionCount);
    setRedistributedPartitions(redistributedPartitions);
    setMessageCorrelationPartitions(messageCorrelationPartitions);
    setScalingPosition(scalingPosition);
    return this;
  }

  public ScaleRecord markPartitionBootstrapped(final int partitionBootstrapped) {
    setRedistributedPartitions(List.of(partitionBootstrapped));
    return this;
  }
}
