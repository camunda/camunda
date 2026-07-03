/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.store.rdbms.metadata.row;

/** Row model for {@code ANALYTICS_METER_ID} (MyBatis maps columns via mapUnderscoreToCamelCase). */
public final class MeterIdRow {

  private long cubeId;
  private String meterName;
  private int aggId;

  public MeterIdRow() {}

  public MeterIdRow(final long cubeId, final String meterName, final int aggId) {
    this.cubeId = cubeId;
    this.meterName = meterName;
    this.aggId = aggId;
  }

  public long getCubeId() {
    return cubeId;
  }

  public void setCubeId(final long cubeId) {
    this.cubeId = cubeId;
  }

  public String getMeterName() {
    return meterName;
  }

  public void setMeterName(final String meterName) {
    this.meterName = meterName;
  }

  public int getAggId() {
    return aggId;
  }

  public void setAggId(final int aggId) {
    this.aggId = aggId;
  }
}
