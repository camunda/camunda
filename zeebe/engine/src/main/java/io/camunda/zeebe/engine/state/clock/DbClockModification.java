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
import io.camunda.zeebe.msgpack.property.ObjectProperty;
import io.camunda.zeebe.protocol.impl.record.value.clock.ClockRecord;
import io.camunda.zeebe.stream.api.StreamClock.ControllableStreamClock.Modification;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class DbClockModification extends UnpackedObject implements DbValue {
  private static final Logger LOGGER = LoggerFactory.getLogger(DbClockModification.class);

  private final EnumProperty<ModificationType> typeProperty =
      new EnumProperty<>("type", ModificationType.class, ModificationType.NONE);
  private final ObjectProperty<ClockRecord> modificationProperty =
      new ObjectProperty<>("modification", new ClockRecord());

  DbClockModification() {
    super(2);
    declareProperty(typeProperty).declareProperty(modificationProperty);
  }

  DbClockModification pinAt(final long pinnedEpoch) {
    reset();
    typeProperty.setValue(ModificationType.PIN);
    modificationProperty.getValue().pinAt(pinnedEpoch);
    return this;
  }

  DbClockModification offsetBy(final long offsetMillis) {
    reset();
    typeProperty.setValue(ModificationType.OFFSET);
    modificationProperty.getValue().offsetBy(offsetMillis);
    return this;
  }

  Modification modification() {
    return switch (typeProperty.getValue()) {
      case NONE -> Modification.none();
      case PIN -> readPin();
      case OFFSET -> readOffset();
    };
  }

  private Modification readPin() {
    final ClockRecord modification = modificationProperty.getValue();
    if (!modificationProperty.isSet() || !modification.hasPinnedEpoch()) {
      LOGGER.warn("Deserialized a PIN clock modification, but there is no pinned epoch");
      return Modification.none();
    }

    return Modification.pinAt(Instant.ofEpochMilli(modification.getPinnedAtEpoch()));
  }

  private Modification readOffset() {
    final ClockRecord modification = modificationProperty.getValue();
    if (!modificationProperty.hasValue() || !modification.hasOffsetMillis()) {
      LOGGER.warn("Deserialized an OFFSET clock modification, but there is no duration");
      return Modification.none();
    }

    return Modification.offsetBy(Duration.ofMillis(modification.getOffsetMillis()));
  }

  // Do not remove values as this could break deserialization in the future
  enum ModificationType {
    NONE,
    PIN,
    OFFSET
  };
}
