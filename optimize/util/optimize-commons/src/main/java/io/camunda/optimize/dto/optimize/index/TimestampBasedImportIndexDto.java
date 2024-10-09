/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.OptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import java.time.OffsetDateTime;

public class TimestampBasedImportIndexDto extends ImportIndexDto<IngestedDataSourceDto>
    implements OptimizeDto {

  protected String esTypeIndexRefersTo;

  public TimestampBasedImportIndexDto(
      final OffsetDateTime lastImportExecutionTimestamp,
      final OffsetDateTime timestampOfLastEntity,
      final String esTypeIndexRefersTo,
      final IngestedDataSourceDto dataSourceDto) {
    super(lastImportExecutionTimestamp, timestampOfLastEntity, dataSourceDto);
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  public TimestampBasedImportIndexDto() {}

  @JsonIgnore
  public String getDataSourceName() {
    return dataSource.getName();
  }

  public String getEsTypeIndexRefersTo() {
    return esTypeIndexRefersTo;
  }

  public void setEsTypeIndexRefersTo(final String esTypeIndexRefersTo) {
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  @Override
  protected boolean canEqual(final Object other) {
    return other instanceof TimestampBasedImportIndexDto;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = super.hashCode();
    final Object $esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    result = result * PRIME + ($esTypeIndexRefersTo == null ? 43 : $esTypeIndexRefersTo.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof TimestampBasedImportIndexDto)) {
      return false;
    }
    final TimestampBasedImportIndexDto other = (TimestampBasedImportIndexDto) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    final Object this$esTypeIndexRefersTo = getEsTypeIndexRefersTo();
    final Object other$esTypeIndexRefersTo = other.getEsTypeIndexRefersTo();
    if (this$esTypeIndexRefersTo == null
        ? other$esTypeIndexRefersTo != null
        : !this$esTypeIndexRefersTo.equals(other$esTypeIndexRefersTo)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    return "TimestampBasedImportIndexDto(esTypeIndexRefersTo=" + getEsTypeIndexRefersTo() + ")";
  }

  public static final class Fields {

    public static final String esTypeIndexRefersTo = "esTypeIndexRefersTo";
  }
}
