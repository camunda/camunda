/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_DATASET_METER_PARAM}. */
public final class MeterParamRow {

  private long cubeId;
  private int meterOrdinal;
  private String paramKey;
  private String paramValue;

  public MeterParamRow() {}

  public MeterParamRow(
      final long cubeId, final int meterOrdinal, final String paramKey, final String paramValue) {
    this.cubeId = cubeId;
    this.meterOrdinal = meterOrdinal;
    this.paramKey = paramKey;
    this.paramValue = paramValue;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public int getMeterOrdinal() {
    return meterOrdinal;
  }

  public void setMeterOrdinal(final int meterOrdinal) {
    this.meterOrdinal = meterOrdinal;
  }

  public String getParamKey() {
    return paramKey;
  }

  public void setParamKey(final String paramKey) {
    this.paramKey = paramKey;
  }

  public String getParamValue() {
    return paramValue;
  }

  public void setParamValue(final String paramValue) {
    this.paramValue = paramValue;
  }
}
