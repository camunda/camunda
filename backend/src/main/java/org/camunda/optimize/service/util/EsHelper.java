/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;


import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.DataSourceDto;
import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class EsHelper {

  public static String constructKey(String elasticSearchType, String engineAlias) {
    return elasticSearchType + "-" + engineAlias;
  }

  public static String constructKey(String elasticSearchType, DataSourceDto dataSourceDto) {
    if (dataSourceDto instanceof ZeebeDataSourceDto) {
      return constructKey(
        elasticSearchType,
        dataSourceDto.getName()
      ) + ((ZeebeDataSourceDto) dataSourceDto).getPartitionId();
    }
    return constructKey(elasticSearchType, dataSourceDto.getName());
  }

}
