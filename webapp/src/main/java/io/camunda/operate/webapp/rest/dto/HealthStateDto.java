/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest.dto;


public class HealthStateDto {

  public static final String HEALTH_STATUS_OK = "OK";

  private String state;

  public HealthStateDto() {
  }

  public HealthStateDto(String state) {
    this.state = state;
  }

  public String getState() {
    return state;
  }

  public void setState(String state) {
    this.state = state;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    HealthStateDto that = (HealthStateDto) o;

    return state != null ? state.equals(that.state) : that.state == null;
  }

  @Override
  public int hashCode() {
    return state != null ? state.hashCode() : 0;
  }
}
