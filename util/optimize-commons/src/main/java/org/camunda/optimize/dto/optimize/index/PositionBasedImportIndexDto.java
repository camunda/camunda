/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.index;

import lombok.Data;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Data
@FieldNameConstants
public class PositionBasedImportIndexDto implements OptimizeDto {

  protected OffsetDateTime lastImportExecutionTimestamp = OffsetDateTime.ofInstant(
    Instant.EPOCH,
    ZoneId.systemDefault()
  );
  protected OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected long positionOfLastEntity = 0;
  protected String esTypeIndexRefersTo;
  protected DataSourceDto dataSourceDto;

}
