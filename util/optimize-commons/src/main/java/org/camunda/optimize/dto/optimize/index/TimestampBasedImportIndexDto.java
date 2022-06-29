/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.index;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;

import java.time.OffsetDateTime;

@NoArgsConstructor
@Data
@EqualsAndHashCode(callSuper = true)
@FieldNameConstants
public class TimestampBasedImportIndexDto extends ImportIndexDto<DataSourceDto> implements EngineImportIndexDto {

  protected String esTypeIndexRefersTo;

  public TimestampBasedImportIndexDto(OffsetDateTime lastImportExecutionTimestamp,
                                      OffsetDateTime timestampOfLastEntity,
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
}
