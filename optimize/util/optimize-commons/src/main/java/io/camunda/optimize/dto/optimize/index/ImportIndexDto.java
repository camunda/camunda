/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  protected String dbTypeIndexRefersTo;

  public ImportIndexDto(
      final OffsetDateTime lastImportExecutionTimestamp,
      final OffsetDateTime timestampOfLastEntity,
      final String dbTypeIndexRefersTo,
      final T dataSource) {
    this.lastImportExecutionTimestamp = lastImportExecutionTimestamp;
    this.timestampOfLastEntity = timestampOfLastEntity;
    this.dbTypeIndexRefersTo = dbTypeIndexRefersTo;
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

  @JsonProperty("esTypeIndexRefersTo")
  public String getDbTypeIndexRefersTo() {
    return dbTypeIndexRefersTo;
  }

  public void setDbTypeIndexRefersTo(final String dbTypeIndexRefersTo) {
    this.dbTypeIndexRefersTo = dbTypeIndexRefersTo;
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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {

    public static final String lastImportExecutionTimestamp = "lastImportExecutionTimestamp";
    public static final String timestampOfLastEntity = "timestampOfLastEntity";
    public static final String dataSource = "dataSource";
  }
}
