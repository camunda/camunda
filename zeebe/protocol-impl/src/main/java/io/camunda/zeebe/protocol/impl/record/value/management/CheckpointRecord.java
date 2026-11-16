/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.management;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointRecordValue;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class CheckpointRecord extends MsgpackBufferValue<CheckpointRecord>
    implements MsgpackRecordValue, CheckpointRecordValue {

  @MsgpackProperty("id")
  long checkpointId = -1L;

  @MsgpackProperty("position")
  long checkpointPosition = -1L;

  @MsgpackProperty("type")
  CheckpointType checkpointType = CheckpointType.MANUAL_BACKUP;

  long firstLogPosition = -1L;
  DirectBuffer snapshotId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getCheckpointId() {
    return checkpointId;
  }

  @Override
  public long getCheckpointPosition() {
    return checkpointPosition;
  }

  @Override
  public CheckpointType getCheckpointType() {
    return checkpointType;
  }

  @Override
  public long getFirstLogPosition() {
    return firstLogPosition;
  }

  @Override
  public String getSnapshotId() {
    return BufferUtil.bufferAsString(snapshotId);
  }

  public CheckpointRecord setSnapshotId(final String snapshotId) {
    this.snapshotId = BufferUtil.wrapString(snapshotId);
    return this;
  }

  public CheckpointRecord setFirstLogPosition(final long firstLogPosition) {
    this.firstLogPosition = firstLogPosition;
    return this;
  }

  public CheckpointRecord setCheckpointType(@Nullable final CheckpointType checkpointType) {
    // the former EnumProperty threw a NullPointerException for null as well
    this.checkpointType = Objects.requireNonNull(checkpointType);
    return this;
  }

  public CheckpointRecord setCheckpointPosition(final long checkpointPosition) {
    this.checkpointPosition = checkpointPosition;
    return this;
  }

  public CheckpointRecord setCheckpointId(final long checkpointId) {
    this.checkpointId = checkpointId;
    return this;
  }

  @Override
  protected MsgpackCodec<CheckpointRecord> codec() {
    return CheckpointRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<CheckpointRecord> ops() {
    return CheckpointRecordOps.INSTANCE;
  }
}
