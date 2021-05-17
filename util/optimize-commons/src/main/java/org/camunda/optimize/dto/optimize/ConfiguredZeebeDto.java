/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.dto.optimize;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ConfiguredZeebeDto extends ConfiguredDataSourceDto {

  private String name;
  private int partitionCount;

  public ConfiguredZeebeDto(final String name, final int partitionCount) {
    super(DataImportSourceType.ZEEBE);
    this.name = name;
    this.partitionCount = partitionCount;
  }

}
