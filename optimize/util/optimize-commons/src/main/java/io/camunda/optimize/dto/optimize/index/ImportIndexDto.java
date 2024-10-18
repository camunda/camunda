/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public abstract class ImportIndexDto<T extends DataSourceDto> implements OptimizeDto {

  protected OffsetDateTime lastImportExecutionTimestamp =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected OffsetDateTime timestampOfLastEntity =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected T dataSource;

  public ImportIndexDto(
      final OffsetDateTime lastImportExecutionTimestamp,
      final OffsetDateTime timestampOfLastEntity,
      final T dataSource) {
    this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
    this.timestampOfLastEntity = timestampOfLastEntity;
    this.dataSource = dataSource;
  }

  public ImportIndexDto() {}

  public OffsetDateTime getLastImportExecutionTimestamp() {
    return lastImportExecutionTimestamp;
  }

  public void setLastImportExecutionTimestamp(final OffsetDateTime lastImportExecutionTimestamp) {
    this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
  }

  public OffsetDateTime getTimestampOfLastEntity() {
    return timestampOfLastEntity;
  }

  public void setTimestampOfLastEntity(final OffsetDateTime timestampOfLastEntity) {
    this.timestampOfLastEntity = timestampOfLastEntity;
  }

  public T getDataSource() {
    return dataSource;
  }

  public void setDataSource(final T dataSource) {
    this.dataSource = dataSource;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof ImportIndexDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final Object $lastImportExecutionTimestamp = getLastImportExecutionTimestamp();
    result =
        result * PRIME
            + ($lastImportExecutionTimestamp == null
                ? 43
                : $lastImportExecutionTimestamp.hashCode());
    final Object $timestampOfLastEntity = getTimestampOfLastEntity();
    result =
        result * PRIME + ($timestampOfLastEntity == null ? 43 : $timestampOfLastEntity.hashCode());
    final Object $dataSource = getDataSource();
    result = result * PRIME + ($dataSource == null ? 43 : $dataSource.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof ImportIndexDto)) {
      return false;
    }
    final ImportIndexDto<?> other = (ImportIndexDto<?>) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$lastImportExecutionTimestamp = getLastImportExecutionTimestamp();
    final Object other$lastImportExecutionTimestamp = other.getLastImportExecutionTimestamp();
    if (this$lastImportExecutionTimestamp == null
        ? other$lastImportExecutionTimestamp != null
        : !this$lastImportExecutionTimestamp.equals(other$lastImportExecutionTimestamp)) {
      return false;
    }
    final Object this$timestampOfLastEntity = getTimestampOfLastEntity();
    final Object other$timestampOfLastEntity = other.getTimestampOfLastEntity();
    if (this$timestampOfLastEntity == null
        ? other$timestampOfLastEntity != null
        : !this$timestampOfLastEntity.equals(other$timestampOfLastEntity)) {
      return false;
    }
    final Object this$dataSource = getDataSource();
    final Object other$dataSource = other.getDataSource();
    if (this$dataSource == null
        ? other$dataSource != null
        : !this$dataSource.equals(other$dataSource)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "ImportIndexDto(lastImportExecutionTimestamp="
        + getLastImportExecutionTimestamp()
        + ", timestampOfLastEntity="
        + getTimestampOfLastEntity()
        + ", dataSource="
        + getDataSource()
        + ")";
  }

  public static final class Fields {

    public static final String lastImportExecutionTimestamp = "lastImportExecutionTimestamp";
    public static final String timestampOfLastEntity = "timestampOfLastEntity";
    public static final String dataSource = "dataSource";
  }
}
