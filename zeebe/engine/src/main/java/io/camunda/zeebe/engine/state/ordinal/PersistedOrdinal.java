/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.ordinal;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.value.ordinal.OrdinalRecord;

public class PersistedOrdinal extends UnpackedObject implements DbValue {

  public static final StringValue ORDINAL_KEY_KEY = new StringValue("ordinalKey");
  public static final StringValue STATUS_KEY = new StringValue("status");

  private final IntegerProperty ordinalKeyProp = new IntegerProperty(ORDINAL_KEY_KEY);

  /** The current status of the ordinal key. */
  private final EnumProperty<OrdinalStatus> statusProp =
      new EnumProperty<>(STATUS_KEY, OrdinalStatus.class);

  // TODO: @yohanfernando >> Other fields to add here
  //  - last completed date (overall across all partitions)
  //  - ILM issued date
  //  - Ordinal delete pending date

  public PersistedOrdinal() {
    super(2);
    declareProperty(ordinalKeyProp);
    declareProperty(statusProp);
  }

  public PersistedOrdinal copy() {
    final var copy = new PersistedOrdinal();
    copy.copyFrom(this);
    return copy;
  }

  public int getOrdinalKey() {
    return ordinalKeyProp.getValue();
  }

  public PersistedOrdinal setOrdinalKey(final int ordinalKey) {
    ordinalKeyProp.setValue(ordinalKey);
    return this;
  }

  public OrdinalStatus getStatus() {
    return statusProp.getValue();
  }

  public PersistedOrdinal setStatus(final OrdinalStatus status) {
    statusProp.setValue(status);
    return this;
  }

  /**
   * Activates this ordinal from the provided OrdinalRecord, setting the ordinal key, status to
   * ACTIVE, and resetting counters.
   *
   * @param ordinalRecord the OrdinalRecord from which to copy the ordinal key
   */
  public void activate(final OrdinalRecord ordinalRecord) {
    ordinalKeyProp.setValue(ordinalRecord.getOrdinalKey());
    statusProp.setValue(OrdinalStatus.ACTIVE);
  }

  public enum OrdinalStatus {
    PENDING,
    ACTIVE,
    CLOSED,
    DELETE_PENDING,
    DELETED
  }
}
