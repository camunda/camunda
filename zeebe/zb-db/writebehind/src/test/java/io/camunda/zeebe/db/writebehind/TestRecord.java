/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;

/**
 * A msgpack value like the engine's state values: its string property keeps a view of the buffer it
 * was read from, and it encodes with a writer that it reuses.
 */
final class TestRecord extends UnpackedObject implements DbValue {

  private final StringProperty name = new StringProperty("name", "");
  private final LongProperty counter = new LongProperty("counter", 0);

  TestRecord() {
    super(2);
    declareProperty(name).declareProperty(counter);
  }

  static TestRecord of(final String name, final long counter) {
    final var record = new TestRecord();
    record.name.setValue(name);
    record.counter.setValue(counter);
    return record;
  }

  String name() {
    return bufferAsString(name.getValue());
  }

  long counter() {
    return counter.getValue();
  }

  TestRecord name(final String value) {
    name.setValue(value);
    return this;
  }

  TestRecord counter(final long value) {
    counter.setValue(value);
    return this;
  }

  /** Another value class that reads the name of a {@link TestRecord}, as a migration might. */
  static final class NameOnly extends UnpackedObject implements DbValue {

    private final StringProperty name = new StringProperty("name", "");

    NameOnly() {
      super(1);
      declareProperty(name);
    }

    String name() {
      return bufferAsString(name.getValue());
    }
  }
}
