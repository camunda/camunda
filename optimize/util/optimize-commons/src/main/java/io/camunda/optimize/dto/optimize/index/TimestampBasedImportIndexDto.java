/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import java.time.OffsetDateTime;

public class TimestampBasedImportIndexDto extends ImportIndexDto<IngestedDataSourceDto>
    implements OptimizeDto {

  protected String dbTypeIndexRefersTo;

  public TimestampBasedImportIndexDto(
      final OffsetDateTime lastImportExecutionTimestamp,
      final OffsetDateTime timestampOfLastEntity,
      final String dbTypeIndexRefersTo,
      final IngestedDataSourceDto dataSourceDto) {
    super(lastImportExecutionTimestamp, timestampOfLastEntity, dataSourceDto);
    this.dbTypeIndexRefersTo = dbTypeIndexRefersTo;
  }

  public TimestampBasedImportIndexDto() {}

  @JsonIgnore
  public String getDataSourceName() {
    return dataSource.getName();
  }

  @JsonProperty("esTypeIndexRefersTo")
  public String getDbTypeIndexRefersTo() {
    return dbTypeIndexRefersTo;
  }

  public void setDbTypeIndexRefersTo(final String dbTypeIndexRefersTo) {
    this.dbTypeIndexRefersTo = dbTypeIndexRefersTo;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof TimestampBasedImportIndexDto;
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
    return "TimestampBasedImportIndexDto(dbTypeIndexRefersTo=" + getDbTypeIndexRefersTo() + ")";
  }

  @SuppressWarnings("checkstyle:ConstantName")
  public static final class Fields {
    // With the support of Opensearch it was decided to get rid of ES prefixes in variable names.
    // However, to avoid reindexing it was decided to keep original field names in indices
    public static final String dbTypeIndexRefersTo = "esTypeIndexRefersTo";
  }
}
