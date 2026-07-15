/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BooleanProperty;
import io.camunda.zeebe.msgpack.property.EnumProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

/**
 * The Model-A materialized row for one element instance (the process instance is just the root
 * element). Upserted on activation with {@code start}, finalized on completion with {@code end},
 * {@code status} and {@code durationMs}, and read by the deriver before it is evicted — so a fact
 * is literally a projection of this row. {@code hadIncident} is stamped on the process row when the
 * instance raises an incident; it is carried onto the completion fact.
 */
public final class ElementEntity extends UnpackedObject implements DbValue {

  private static final long UNSET = -1L;

  private final LongProperty startProp = new LongProperty("start", UNSET);
  private final LongProperty endProp = new LongProperty("end", UNSET);
  private final LongProperty durationProp = new LongProperty("duration", UNSET);
  private final LongProperty parentScopeProp = new LongProperty("parentScope", UNSET);
  private final EnumProperty<ElementStatus> statusProp =
      new EnumProperty<>("status", ElementStatus.class, ElementStatus.ACTIVE);
  private final BooleanProperty isProcessProp = new BooleanProperty("isProcess", false);
  private final BooleanProperty hadIncidentProp = new BooleanProperty("hadIncident", false);
  // The business value read at ACTIVATION time (process rows only; unset when the value variable
  // was absent or non-numeric then). The end fact subtracts exactly this, never the variable's
  // current value — otherwise a value created or changed mid-flight would leave a permanent
  // residue on the value-in-flight level.
  private final LongProperty valueProp = new LongProperty("value", UNSET);

  public ElementEntity() {
    super(8);
    declareProperty(startProp)
        .declareProperty(endProp)
        .declareProperty(durationProp)
        .declareProperty(parentScopeProp)
        .declareProperty(statusProp)
        .declareProperty(isProcessProp)
        .declareProperty(hadIncidentProp)
        .declareProperty(valueProp);
  }

  /**
   * Resets to a fresh active row: {@code {start, isProcess, parentScope, ACTIVE}} with the terminal
   * fields unset and {@code hadIncident=false}. Callers reusing a flyweight must start here so no
   * field of a previous row leaks in. {@code parentScopeKey} is the element's flow scope, used to
   * resolve variables up the scope hierarchy on completion.
   */
  public ElementEntity activate(
      final long startTimeMs, final boolean isProcess, final long parentScopeKey) {
    reset();
    startProp.setValue(startTimeMs);
    isProcessProp.setValue(isProcess);
    parentScopeProp.setValue(parentScopeKey);
    statusProp.setValue(ElementStatus.ACTIVE);
    return this;
  }

  public ElementEntity complete(final long endTimeMs, final ElementStatus status) {
    endProp.setValue(endTimeMs);
    durationProp.setValue(endTimeMs - startProp.getValue());
    statusProp.setValue(status);
    return this;
  }

  public ElementEntity hadIncident(final boolean hadIncident) {
    hadIncidentProp.setValue(hadIncident);
    return this;
  }

  /** Materializes the activation-time business value (see the field comment). */
  public ElementEntity value(final long value) {
    valueProp.setValue(value);
    return this;
  }

  public boolean hasValue() {
    return valueProp.getValue() != UNSET;
  }

  public long value() {
    return valueProp.getValue();
  }

  public long start() {
    return startProp.getValue();
  }

  public long end() {
    return endProp.getValue();
  }

  public long durationMs() {
    return durationProp.getValue();
  }

  public long parentScopeKey() {
    return parentScopeProp.getValue();
  }

  public ElementStatus status() {
    return statusProp.getValue();
  }

  public boolean isProcess() {
    return isProcessProp.getValue();
  }

  public boolean hadIncident() {
    return hadIncidentProp.getValue();
  }
}
