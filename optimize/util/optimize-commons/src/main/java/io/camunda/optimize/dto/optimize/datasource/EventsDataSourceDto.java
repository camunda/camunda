/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.datasource;

import io.camunda.optimize.dto.optimize.DataImportSourceType;

public class EventsDataSourceDto extends DataSourceDto {

  public EventsDataSourceDto() {
    this(null);
  }

  public EventsDataSourceDto(final String name) {
    super(DataImportSourceType.EVENTS, name);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof EventsDataSourceDto;
  }

  @Override
  public int hashCode() {
    final int result = super.hashCode();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof EventsDataSourceDto)) {
      return false;
    }
    final EventsDataSourceDto other = (EventsDataSourceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "EventsDataSourceDto(super=" + super.toString() + ")";
  }
}
