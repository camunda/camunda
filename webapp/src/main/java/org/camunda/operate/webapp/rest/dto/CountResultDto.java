/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.webapp.rest.dto;

public class CountResultDto {

  private long count;

  public CountResultDto() {
  }
  
  public CountResultDto(long count) {
    this.count = count;
  }

  public long getCount() {
    return count;
  }

  public void setCount(long count) {
    this.count = count;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;

    CountResultDto that = (CountResultDto) o;

    return count == that.count;
  }

  @Override
  public int hashCode() {
    return (int) (count ^ (count >>> 32));
  }
}
