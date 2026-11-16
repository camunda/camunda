/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.scaling;

import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.scaling.ScaleRecordValue;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.collections.IntArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ScaleRecord extends MsgpackBufferValue<ScaleRecord>
    implements MsgpackRecordValue, ScaleRecordValue {

  int desiredPartitionCount = -1;

  // partitions that have been activated because of this scale up operation
  // activated = completed redistribution
  final IntArrayList redistributedPartitions = new IntArrayList();

  // partitions that are ready to take part of processing completely:
  // ready = completed relocation
  final IntArrayList relocatedPartitions = new IntArrayList();

  /**
   * Represent the number of partitions that participate in message correlation. The set of
   * partitions is in the range [1, messageCorrelationPartitions]
   */
  int messageCorrelationPartitions = -1;

  long scalingPosition = -1L;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public int getDesiredPartitionCount() {
    return desiredPartitionCount;
  }

  public ScaleRecord setDesiredPartitionCount(final int desiredPartitionCount) {
    this.desiredPartitionCount = desiredPartitionCount;
    return this;
  }

  @Override
  public SortedSet<Integer> getRedistributedPartitions() {
    return IntStream.range(0, redistributedPartitions.size())
        .mapToObj(redistributedPartitions::getInt)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  @Override
  public Collection<Integer> getRelocatedPartitions() {
    return IntStream.range(0, relocatedPartitions.size())
        .mapToObj(relocatedPartitions::getInt)
        .toList();
  }

  public ScaleRecord setRelocatedPartitions(final Collection<Integer> partitions) {
    relocatedPartitions.clear();
    for (final int partition : partitions) {
      relocatedPartitions.addInt(partition);
    }
    return this;
  }

  @Override
  public int getMessageCorrelationPartitions() {
    return messageCorrelationPartitions;
  }

  @Override
  public long getScalingPosition() {
    return scalingPosition;
  }

  public ScaleRecord setScalingPosition(final long position) {
    scalingPosition = position;
    return this;
  }

  public ScaleRecord setMessageCorrelationPartitions(final int messageCorrelationPartitions) {
    this.messageCorrelationPartitions = messageCorrelationPartitions;
    return this;
  }

  public ScaleRecord setRedistributedPartitions(final Collection<Integer> partitions) {
    redistributedPartitions.clear();
    for (final int partition : partitions) {
      redistributedPartitions.addInt(partition);
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

  @Override
  protected MsgpackCodec<ScaleRecord> codec() {
    return ScaleRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ScaleRecord> ops() {
    return ScaleRecordOps.INSTANCE;
  }
}
