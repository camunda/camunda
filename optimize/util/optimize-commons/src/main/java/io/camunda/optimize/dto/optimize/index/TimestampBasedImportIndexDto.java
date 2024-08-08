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
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class TimestampBasedImportIndexDto extends ImportIndexDto<IngestedDataSourceDto>
    implements OptimizeDto {

  protected String esTypeIndexRefersTo;

  public TimestampBasedImportIndexDto(
      OffsetDateTime lastImportExecutionTimestamp,
      OffsetDateTime timestampOfLastEntity,
      final String esTypeIndexRefersTo,
      final IngestedDataSourceDto dataSourceDto) {
    super(lastImportExecutionTimestamp, timestampOfLastEntity, dataSourceDto);
    this.esTypeIndexRefersTo = esTypeIndexRefersTo;
  }

  @JsonIgnore
  public String getDataSourceName() {
    return dataSource.getName();
  }
}
