/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize.datasource;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.camunda.optimize.dto.optimize.DataImportSourceType;
import org.camunda.optimize.dto.optimize.SchedulerConfig;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class IngestedDataSourceDto extends DataSourceDto implements SchedulerConfig {

  public IngestedDataSourceDto() {
    super(DataImportSourceType.INGESTED_DATA, null);
  }

  public IngestedDataSourceDto(final String name) {
    super(DataImportSourceType.INGESTED_DATA, name);
  }

}
