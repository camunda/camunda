/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseHelper {

  public static String constructKey(String databaseType, String engineAlias) {
    return databaseType + "-" + engineAlias;
  }

  public static String constructKey(String databaseType, DataSourceDto dataSourceDto) {
    if (dataSourceDto instanceof ZeebeDataSourceDto) {
      return constructKey(databaseType, dataSourceDto.getName())
          + ((ZeebeDataSourceDto) dataSourceDto).getPartitionId();
    }
    return constructKey(databaseType, dataSourceDto.getName());
  }
}
