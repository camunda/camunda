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
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
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
  long flowScopeKey = -1L;
  int retries = 0;
  boolean interrupted = false;
  RecordKind state = RecordKind.ONE;
  final UnsafeBuffer tenantId = new UnsafeBuffer("<default>".getBytes());

  @MsgpackProperty(binary = true)
  final UnsafeBuffer payload = new UnsafeBuffer(Decoding.EMPTY);

  @MsgpackProperty(raw = true)
  final UnsafeBuffer raw = new UnsafeBuffer(Decoding.EMPTY);

  final List<DirectBuffer> tags = new ArrayList<>();
  final GeneratedChild nested = new GeneratedChild();
  final LegacyChild legacy = new LegacyChild();

  @MsgpackUnknownProperties final UnknownProperties unknown = new UnknownProperties();

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
    if (!skipped.equals("key")) {
      key = 2251799813685249L;
    }
    if (!skipped.equals("parentKey")) {
      parentKey = Long.MIN_VALUE + 1;
    }
    if (!skipped.equals("partitionId")) {
      partitionId = 3;
    }
    if (!skipped.equals("active")) {
      active = true;
    }
    if (!skipped.equals("kind")) {
      kind = RecordKind.THREE;
    }
    if (!skipped.equals("name")) {
      name = new UnsafeBuffer("task-7".getBytes());
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
