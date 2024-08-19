/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.datasource;

import io.camunda.optimize.dto.optimize.DataImportSourceType;

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
    final int PRIME = 59;
    int result = super.hashCode();
    result = result * PRIME + getPartitionId();
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ZeebeDataSourceDto)) {
      return false;
    }
    final ZeebeDataSourceDto other = (ZeebeDataSourceDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    if (getPartitionId() != other.getPartitionId()) {
      return false;
    }
    return true;
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
