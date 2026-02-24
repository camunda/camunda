/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.hubmetrics;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.IntegerProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;

public class ElementMetricsValue extends UnpackedObject implements DbValue {

  private final IntegerProperty createdProp = new IntegerProperty("created", 0);
  private final IntegerProperty completedProp = new IntegerProperty("completed", 0);
  private final LongProperty sumDurationProp = new LongProperty("sumDuration", 0);
  private final LongProperty maxDurationProp = new LongProperty("maxDuration", 0);
  private final LongProperty minDurationProp = new LongProperty("minDuration", 0);

  public ElementMetricsValue() {
    super(5);
    declareProperty(createdProp)
        .declareProperty(completedProp)
        .declareProperty(sumDurationProp)
        .declareProperty(maxDurationProp)
        .declareProperty(minDurationProp);
  }

  public ElementMetricsValue wrap(final ElementMetricsValue value) {
    setCompleted(value.getCompleted());
    setCreated(value.getCreated());
    return this;
  }

  public int getCreated() {
    return createdProp.getValue();
  }

  public ElementMetricsValue setCreated(final int value) {
    createdProp.setValue(value);
    return this;
  }

  public void incrementCreated() {
    final var currentCreatedCounter = getCreated();
    setCreated(currentCreatedCounter + 1);
  }

  public int getCompleted() {
    return completedProp.getValue();
  }

  public ElementMetricsValue setCompleted(final int value) {
    completedProp.setValue(value);
    return this;
  }

  public void incrementCompleted() {
    final var currentCompletedCounter = getCompleted();
    setCompleted(currentCompletedCounter + 1);
  }

  public long getSumDuration() {
    return sumDurationProp.getValue();
  }

  public ElementMetricsValue setSumDuration(final long value) {
    sumDurationProp.setValue(value);
    return this;
  }

  public void addDuration(final long value) {
    final var newValue = getSumDuration() + value;
    setSumDuration(newValue);
  }

  public long getMaxDuration() {
    return maxDurationProp.getValue();
  }

  public ElementMetricsValue setMaxDuration(final long value) {
    maxDurationProp.setValue(value);
    return this;
  }

  public void setMaxDurationIfHigher(final long value) {
    final var newValue = Math.max(getMaxDuration(), value);
    setMaxDuration(newValue);
  }

  public long getMinDuration() {
    return minDurationProp.getValue();
  }

  public ElementMetricsValue setMinDuration(final long value) {
    minDurationProp.setValue(value);
    return this;
  }

  public void setMinDurationIfLower(final long value) {
    final var newValue = Math.max(Math.min(getSumDuration(), value), 0);
    setMinDuration(newValue);
  }
}
