/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.index;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;
import org.camunda.optimize.dto.optimize.OptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@FieldNameConstants
@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class ImportIndexDto<T extends DataSourceDto> implements OptimizeDto {
  protected OffsetDateTime lastImportExecutionTimestamp = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected OffsetDateTime timestampOfLastEntity = OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected T dataSource;
}
