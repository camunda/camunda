/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
