/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class TimestampBasedEngineImportIndexHandler
  extends TimestampBasedDataSourceImportIndexHandler<EngineDataSourceDto> {

  protected EngineDataSourceDto getDataSource() {
    return new EngineDataSourceDto(getEngineAlias());
  }

}
