/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import io.camunda.optimize.dto.optimize.datasource.EventsDataSourceDto;
import io.camunda.optimize.service.db.DatabaseConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;

@RequiredArgsConstructor
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public abstract class TimestampBasedEventDataImportIndexHandler
    extends TimestampBasedDataSourceImportIndexHandler<EventsDataSourceDto> {

  @Override
  public String getEngineAlias() {
    return DatabaseConstants.ENGINE_ALIAS_OPTIMIZE;
  }

  @Override
  public EventsDataSourceDto getDataSource() {
    return new EventsDataSourceDto(getEngineAlias());
  }
}
