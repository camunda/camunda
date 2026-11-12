/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.microbenchmarks.msgpack;

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
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;

/**
 * Hand-written {@link UnpackedObject} with every field type the generator supports, and the stored
 * shape of {@link PojoFull}.
 */
public final class UnpackedFull extends UnpackedObject {

  private final LongProperty keyProp = new LongProperty("key", -1L);
  private final IntegerProperty countProp = new IntegerProperty("count", 1);
  private final BooleanProperty flagProp = new BooleanProperty("flag", false);
  private final EnumProperty<SampleKind> kindProp =
      new EnumProperty<>("kind", SampleKind.class, SampleKind.FIRST);
  private final StringProperty textProp = new StringProperty("text", "");
  private final BinaryProperty blobProp = new BinaryProperty("blob");
  private final PackedProperty rawProp = new PackedProperty("raw");
  private final ArrayProperty<StringValue> idsProp = new ArrayProperty<>("ids", StringValue::new);
  private final ObjectProperty<UnpackedNested> nestedProp =
      new ObjectProperty<>("nested", new UnpackedNested());
  private final ObjectProperty<UnpackedNested> legacyProp =
      new ObjectProperty<>("legacy", new UnpackedNested());

  public UnpackedFull() {
    super(10);
    declareProperty(keyProp)
        .declareProperty(countProp)
        .declareProperty(flagProp)
        .declareProperty(kindProp)
        .declareProperty(textProp)
        .declareProperty(blobProp)
        .declareProperty(rawProp)
        .declareProperty(idsProp)
        .declareProperty(nestedProp)
        .declareProperty(legacyProp);
  }

  public UnpackedFull fill(final int seed) {
    keyProp.setValue(2_251_799_813_685_300L + seed);
    countProp.setValue(1 + seed % 5);
    flagProp.setValue(seed % 2 == 0);
    kindProp.setValue(SampleKind.values()[seed % SampleKind.values().length]);
    textProp.setValue("text-" + seed);
    final DirectBuffer blob = BufferUtil.wrapString("{\"orderId\":" + seed + "}");
    blobProp.setValue(blob, 0, blob.capacity());
    rawProp.setValue(PojoFull.RAW, 0, PojoFull.RAW.capacity());
    for (int i = 0; i < 3; i++) {
      idsProp.add().wrap(BufferUtil.wrapString("id-" + seed + "-" + i));
    }
    nestedProp.getValue().fill(seed);
    legacyProp.getValue().fill(seed + 1);
    return this;
  }
}
