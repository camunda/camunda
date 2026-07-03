/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_METER}. */
public final class MeterRow {

  private long cubeId;
  private int ordinal;
  private String name;
  private String meterType;
  private String measureField;

  public MeterRow() {}

  public MeterRow(
      final long cubeId,
      final int ordinal,
      final String name,
      final String meterType,
      final String measureField) {
    this.cubeId = cubeId;
    this.ordinal = ordinal;
    this.name = name;
    this.meterType = meterType;
    this.measureField = measureField;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public String getMeterType() {
    return meterType;
  }

  public void setMeterType(final String meterType) {
    this.meterType = meterType;
  }

  public String getMeasureField() {
    return measureField;
  }

  public void setMeasureField(final String measureField) {
    this.measureField = measureField;
  }
}
