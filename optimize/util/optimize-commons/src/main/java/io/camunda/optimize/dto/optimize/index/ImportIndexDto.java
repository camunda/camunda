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
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public abstract class ImportIndexDto<T extends DataSourceDto> implements OptimizeDto {

  protected OffsetDateTime lastImportExecutionTimestamp =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected OffsetDateTime timestampOfLastEntity =
      OffsetDateTime.ofInstant(Instant.EPOCH, ZoneId.systemDefault());
  protected T dataSource;

  public static final class Fields {

    public static final String lastImportExecutionTimestamp = "lastImportExecutionTimestamp";
    public static final String timestampOfLastEntity = "timestampOfLastEntity";
    public static final String dataSource = "dataSource";
  }
}
