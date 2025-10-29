/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.datasource;

import io.camunda.optimize.dto.optimize.DataImportSourceType;
import java.util.Objects;

public class ZeebeDataSourceDto extends DataSourceDto {

  private int partitionId;

  public ZeebeDataSourceDto() {
    super(DataImportSourceType.ZEEBE, null);
  }

  public ZeebeDataSourceDto(final String name, final int partitionId) {
    super(DataImportSourceType.ZEEBE, name);
    this.partitionId = partitionId;
  }

  public int getPartitionId() {
    return partitionId;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof ZeebeDataSourceDto;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), partitionId);
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final ZeebeDataSourceDto that = (ZeebeDataSourceDto) o;
    return partitionId == that.partitionId;
  }

  @Override
  public String toString() {
    return "ZeebeDataSourceDto(super="
        + super.toString()
        + ", partitionId="
        + getPartitionId()
        + ")";
  }
}
