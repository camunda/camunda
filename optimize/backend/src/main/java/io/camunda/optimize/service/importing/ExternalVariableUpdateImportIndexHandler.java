/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ENGINE_ALIAS_OPTIMIZE;

import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler
    extends TimestampBasedDataSourceImportIndexHandler<IngestedDataSourceDto> {

  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID =
      "externalVariableUpdateImportIndex";

  @Override
  public String getEngineAlias() {
    return ENGINE_ALIAS_OPTIMIZE;
  }

  @Override
  protected String getDatabaseDocID() {
    return EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }

  @Override
  protected IngestedDataSourceDto getDataSource() {
    return new IngestedDataSourceDto(getEngineAlias());
  }
}
