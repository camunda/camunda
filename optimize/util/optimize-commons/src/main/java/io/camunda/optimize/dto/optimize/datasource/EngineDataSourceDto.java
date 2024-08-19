/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.datasource;

import io.camunda.optimize.dto.optimize.DataImportSourceType;
import io.camunda.optimize.dto.optimize.SchedulerConfig;

public class EngineDataSourceDto extends DataSourceDto implements SchedulerConfig {

  public EngineDataSourceDto() {
    this(null);
  }

  public EngineDataSourceDto(final String engineAlias) {
    super(DataImportSourceType.ENGINE, engineAlias);
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof EngineDataSourceDto;
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
    if (!(o instanceof EngineDataSourceDto)) {
      return false;
    }
    final EngineDataSourceDto other = (EngineDataSourceDto) o;
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
    return "EngineDataSourceDto(super=" + super.toString() + ")";
  }
}
