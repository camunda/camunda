/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest.dto;

public class HealthStateDto {

  public static final String HEALTH_STATUS_OK = "OK";

  private String state;

  public HealthStateDto() {}

  public HealthStateDto(final String state) {
    this.state = state;
  }

  public String getState() {
    return state;
  }

  public void setState(final String state) {
    this.state = state;
  }

  @Override
  public int hashCode() {
    return state != null ? state.hashCode() : 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final HealthStateDto that = (HealthStateDto) o;

    return state != null ? state.equals(that.state) : that.state == null;
  }
}
