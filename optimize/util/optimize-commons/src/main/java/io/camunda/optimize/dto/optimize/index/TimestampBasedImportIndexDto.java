/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
public class TimestampBasedImportIndexDto extends ImportIndexDto<DataSourceDto>
    implements EngineImportIndexDto {

  protected String esTypeIndexRefersTo;

  public TimestampBasedImportIndexDto(
      final OffsetDateTime lastImportExecutionTimestamp,
      final OffsetDateTime timestampOfLastEntity,
      final String esTypeIndexRefersTo,
      final DataSourceDto dataSourceDto) {
    super(lastImportExecutionTimestamp, timestampOfLastEntity, dataSourceDto);
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  @Override
  @JsonIgnore
  public String getEngine() {
    return dataSource.getName();
  }

  public static final class Fields {

    public static final String esTypeIndexRefersTo = "esTypeIndexRefersTo";
  }
}
