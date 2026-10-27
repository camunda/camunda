/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.clock;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;

final class DbClockModification extends UnpackedObject implements DbValue {
  // depending on the intent, the value may be a timestamp (epoch milliseconds) or an offset (in
  // milliseconds)
  private final LongProperty timeProperty = new LongProperty("time", 0);
  private final EnumProperty<ModificationType> typeProperty =
      new EnumProperty<>("type", ModificationType.class, ModificationType.NONE);

  DbClockModification() {
    super(2);
    declareProperty(typeProperty).declareProperty(timeProperty);
  }

  DbClockModification pinAt(final long pinnedEpoch) {
    reset();
    typeProperty.setValue(ModificationType.PIN);
    timeProperty.setValue(pinnedEpoch);
    return this;
  }

  DbClockModification offsetBy(final long offsetMillis) {
    reset();
    typeProperty.setValue(ModificationType.OFFSET);
    timeProperty.setValue(offsetMillis);
    return this;
  }

  Modification modification() {
    return switch (typeProperty.getValue()) {
      case NONE -> Modification.none();
      case PIN -> Modification.pinAt(Instant.ofEpochMilli(timeProperty.getValue()));
      case OFFSET -> Modification.offsetBy(Duration.ofMillis(timeProperty.getValue()));
    };
  }

  // Do not remove values as this could break deserialization in the future
  enum ModificationType {
    NONE,
    PIN,
    OFFSET
  };
}
