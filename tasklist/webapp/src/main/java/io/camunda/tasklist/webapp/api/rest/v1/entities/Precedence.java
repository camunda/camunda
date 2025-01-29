/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.api.rest.v1.entities;

public class Precedence {
  Double high;
  Double medium;
  Double low;

  public Precedence(Double high, Double medium, Double low) {
    this.high = high;
    this.medium = medium;
    this.low = low;
  }

  public Double getHigh() {
    return high;
  }

  public void setHigh(Double high) {
    this.high = high;
  }

  public Double getMedium() {
    return medium;
  }

  public void setMedium(Double medium) {
    this.medium = medium;
  }
  public Double getLow() {
    return low;
  }
  public void setLow(Double low) {
    this.low = low;
  }
}
