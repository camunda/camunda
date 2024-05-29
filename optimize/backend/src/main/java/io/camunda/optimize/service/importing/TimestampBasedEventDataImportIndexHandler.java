/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
