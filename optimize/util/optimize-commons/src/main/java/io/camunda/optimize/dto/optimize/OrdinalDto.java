/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize;

import java.time.OffsetDateTime;

/**
 * Document stored in the ordinal index. The {@code ordinal} value is the short counter incremented
 * every minute by the Zeebe engine. The {@code timestamp} is the engine-side epoch-millis when the
 * ordinal was ticked and the {@code dateTime} is the human-readable version.
 */
public class OrdinalDto implements OptimizeDto {

  private int ordinal;
  private long timestampMs;
  private OffsetDateTime dateTime;

  public OrdinalDto() {}

  public OrdinalDto(final int ordinal, final long timestampMs, final OffsetDateTime dateTime) {
    this.ordinal = ordinal;
    this.timestampMs = timestampMs;
    this.dateTime = dateTime;
  }

  public int getOrdinal() {
    return ordinal;
  }

  public void setOrdinal(final int ordinal) {
    this.ordinal = ordinal;
  }

  public long getTimestampMs() {
    return timestampMs;
  }

  public void setTimestampMs(final long timestampMs) {
    this.timestampMs = timestampMs;
  }

  public OffsetDateTime getDateTime() {
    return dateTime;
  }

  public void setDateTime(final OffsetDateTime dateTime) {
    this.dateTime = dateTime;
  }
}
