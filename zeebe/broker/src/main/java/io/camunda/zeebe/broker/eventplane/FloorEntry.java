/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.eventplane;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.eventplane.publisher.UploadFloor;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.LongProperty;
import java.util.UUID;

/** The upload floor as the state holds it. At index 0, it is held: nothing was uploaded yet. */
final class FloorEntry extends UnpackedObject implements DbValue {

  private static final long HELD_INDEX = 0;

  private final LongProperty historyMostSigBitsProp = new LongProperty("historyMostSigBits", 0);
  private final LongProperty historyLeastSigBitsProp = new LongProperty("historyLeastSigBits", 0);
  private final LongProperty indexProp = new LongProperty("index", HELD_INDEX);
  private final LongProperty positionProp = new LongProperty("position", FloorState.HELD);

  FloorEntry() {
    super(4);
    declareProperty(historyMostSigBitsProp)
        .declareProperty(historyLeastSigBitsProp)
        .declareProperty(indexProp)
        .declareProperty(positionProp);
  }

  boolean isHeld() {
    return indexProp.getValue() == HELD_INDEX;
  }

  long index() {
    return indexProp.getValue();
  }

  long position() {
    return positionProp.getValue();
  }

  UploadFloor toFloor() {
    return new UploadFloor(
        new UUID(historyMostSigBitsProp.getValue(), historyLeastSigBitsProp.getValue()),
        indexProp.getValue(),
        positionProp.getValue());
  }

  FloorEntry hold() {
    historyMostSigBitsProp.setValue(0);
    historyLeastSigBitsProp.setValue(0);
    indexProp.setValue(HELD_INDEX);
    positionProp.setValue(FloorState.HELD);
    return this;
  }

  FloorEntry set(final UploadFloor floor) {
    historyMostSigBitsProp.setValue(floor.history().getMostSignificantBits());
    historyLeastSigBitsProp.setValue(floor.history().getLeastSignificantBits());
    indexProp.setValue(floor.index());
    positionProp.setValue(floor.position());
    return this;
  }
}
