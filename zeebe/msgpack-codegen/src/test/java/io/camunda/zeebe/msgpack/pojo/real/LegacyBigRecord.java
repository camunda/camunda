/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.pojo.real;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.msgpack.property.PackedProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import org.agrona.concurrent.UnsafeBuffer;

/** The big record as an {@link UnpackedObject}: properties without a default are required. */
public final class LegacyBigRecord extends UnpackedObject implements BigRecord {
  private final LongProperty key = new LongProperty("key");
  private final LongProperty parentKey = new LongProperty("parentKey");
  private final IntegerProperty partitionId = new IntegerProperty("partitionId");
  private final BooleanProperty active = new BooleanProperty("active");
  private final EnumProperty<RecordKind> kind = new EnumProperty<>("kind", RecordKind.class);
  private final StringProperty name = new StringProperty("name");
  private final BinaryProperty data = new BinaryProperty("data");
  private final LongProperty flowScopeKey = new LongProperty("flowScopeKey", -1L);
  private final IntegerProperty retries = new IntegerProperty("retries", 0);
  private final BooleanProperty interrupted = new BooleanProperty("interrupted", false);
  private final EnumProperty<RecordKind> state =
      new EnumProperty<>("state", RecordKind.class, RecordKind.ONE);
  private final StringProperty tenantId = new StringProperty("tenantId", "<default>");
  private final BinaryProperty payload = new BinaryProperty("payload");
  private final PackedProperty raw = new PackedProperty("raw");
  private final ArrayProperty<StringValue> tags = new ArrayProperty<>("tags", StringValue::new);
  private final ObjectProperty<LegacyChild> nested =
      new ObjectProperty<>("nested", new LegacyChild());
  private final ObjectProperty<LegacyChild> legacy =
      new ObjectProperty<>("legacy", new LegacyChild());

  public LegacyBigRecord() {
    super(17);
    declareProperty(key)
        .declareProperty(parentKey)
        .declareProperty(partitionId)
        .declareProperty(active)
        .declareProperty(kind)
        .declareProperty(name)
        .declareProperty(data)
        .declareProperty(flowScopeKey)
        .declareProperty(retries)
        .declareProperty(interrupted)
        .declareProperty(state)
        .declareProperty(tenantId)
        .declareProperty(payload)
        .declareProperty(raw)
        .declareProperty(tags)
        .declareProperty(nested)
        .declareProperty(legacy);
  }

  @Override
  public long getKey() {
    return key.getValue();
  }

  @Override
  public int getPartitionId() {
    return partitionId.getValue();
  }

  @Override
  public void fillExcept(final String skipped) {
    if (!"key".equals(skipped)) {
      key.setValue(2251799813685249L);
    }
    if (!"parentKey".equals(skipped)) {
      parentKey.setValue(Long.MIN_VALUE + 1);
    }
    if (!"partitionId".equals(skipped)) {
      partitionId.setValue(3);
    }
    if (!"active".equals(skipped)) {
      active.setValue(true);
    }
    if (!"kind".equals(skipped)) {
      kind.setValue(RecordKind.THREE);
    }
    if (!"name".equals(skipped)) {
      name.setValue("task-7");
    }
    if (!"data".equals(skipped)) {
      data.setValue(new UnsafeBuffer(new byte[] {1, 2}), 0, 2);
    }
    flowScopeKey.setValue(2251799813685300L);
    retries.setValue(2);
    interrupted.setValue(true);
    state.setValue(RecordKind.TWO);
    tenantId.setValue("tenant-a");
    final var json = new UnsafeBuffer("{\"orderId\":1}".getBytes());
    payload.setValue(json, 0, json.capacity());
    final var packed = new UnsafeBuffer(new byte[] {(byte) 0xa3, 'a', 'b', 'c'});
    raw.setValue(packed, 0, packed.capacity());
    for (int i = 0; i < 3; i++) {
      tags.add().wrap(new UnsafeBuffer(("tag-" + i).getBytes()));
    }
    nested.getValue().fill(5);
    legacy.getValue().fill(6);
  }
}
