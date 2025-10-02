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
import java.util.Objects;

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
    return Objects.hash(getClass(), super.hashCode());
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public String toString() {
    return "EngineDataSourceDto(super=" + super.toString() + ")";
  }
}
