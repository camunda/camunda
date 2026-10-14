/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * The data of a system partition log entry: a value put under a key. It is internal to the system
 * partition and never exported, so it is a plain msgpack object rather than a protocol record.
 */
final class SystemPartitionEntry extends UnpackedObject {
  private final LongProperty keyProp = new LongProperty("key", -1L);
  private final BinaryProperty valueProp = new BinaryProperty("value", new UnsafeBuffer());

  SystemPartitionEntry() {
    super(2);
    declareProperty(keyProp).declareProperty(valueProp);
  }

  long getKey() {
    return keyProp.getValue();
  }

  SystemPartitionEntry setKey(final long key) {
    keyProp.setValue(key);
    return this;
  }

  DirectBuffer getValue() {
    return valueProp.getValue();
  }

  SystemPartitionEntry setValue(final DirectBuffer value) {
    valueProp.setValue(value);
    return this;
  }
}
