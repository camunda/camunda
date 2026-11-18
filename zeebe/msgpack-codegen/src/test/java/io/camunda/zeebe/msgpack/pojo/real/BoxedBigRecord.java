/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.pojo.annotation.*;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The big record as a generated POJO. Required longs and ints as {@code @Nullable} boxed values.
 */
@MsgpackPojo
@NullMarked
public final class BoxedBigRecord extends MsgpackBufferValue<BoxedBigRecord> implements BigRecord {
  @Nullable Long key;
  @Nullable Long parentKey;
  @Nullable Integer partitionId;
  @Nullable Boolean active;
  @Nullable RecordKind kind;
  @Nullable DirectBuffer name;

  @Binary @Nullable DirectBuffer data;

  long flowScopeKey = -1L;
  int retries = 0;
  boolean interrupted = false;
  RecordKind state = RecordKind.ONE;
  final UnsafeBuffer tenantId = new UnsafeBuffer("<default>".getBytes());

  @Binary final UnsafeBuffer payload = new UnsafeBuffer(Decoding.EMPTY);

  @Raw final UnsafeBuffer raw = new UnsafeBuffer(Decoding.EMPTY);

  final List<DirectBuffer> tags = new ArrayList<>();
  final GeneratedChild nested = new GeneratedChild();
  final LegacyChild legacy = new LegacyChild();

  final UnknownProperties unknown = new UnknownProperties();

  @Override
  protected MsgpackCodec<BoxedBigRecord> codec() {
    return BoxedBigRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<BoxedBigRecord> ops() {
    return BoxedBigRecordOps.INSTANCE;
  }

  @Override
  public long getKey() {
    return Decoding.required(key, "key");
  }

  @Override
  public int getPartitionId() {
    return Decoding.required(partitionId, "partitionId");
  }

  @Override
  public void fillExcept(final String skipped) {
    if (!"key".equals(skipped)) {
      key = 2251799813685249L;
    }
    if (!"parentKey".equals(skipped)) {
      parentKey = Long.MIN_VALUE + 1;
    }
    if (!"partitionId".equals(skipped)) {
      partitionId = 3;
    }
    if (!"active".equals(skipped)) {
      active = true;
    }
    if (!"kind".equals(skipped)) {
      kind = RecordKind.THREE;
    }
    if (!"name".equals(skipped)) {
      name = new UnsafeBuffer("task-7".getBytes());
    }
    if (!"data".equals(skipped)) {
      data = new UnsafeBuffer(new byte[] {1, 2});
    }
    flowScopeKey = 2251799813685300L;
    retries = 2;
    interrupted = true;
    state = RecordKind.TWO;
    tenantId.wrap("tenant-a".getBytes());
    payload.wrap("{\"orderId\":1}".getBytes());
    raw.wrap(new byte[] {(byte) 0xa3, 'a', 'b', 'c'});
    for (int i = 0; i < 3; i++) {
      tags.add(new UnsafeBuffer(("tag-" + i).getBytes()));
    }
    nested.fill(5);
    legacy.fill(6);
  }
}
