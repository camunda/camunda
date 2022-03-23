/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.dto.optimize.datasource;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import org.camunda.optimize.dto.optimize.DataImportSourceType;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ZeebeDataSourceDto extends DataSourceDto {

  private int partitionId;

  public ZeebeDataSourceDto() {
    super(DataImportSourceType.ZEEBE, null);
  }

  public ZeebeDataSourceDto(final String name, final int partitionId) {
    super(DataImportSourceType.ZEEBE, name);
    this.partitionId = partitionId;
  }

}
