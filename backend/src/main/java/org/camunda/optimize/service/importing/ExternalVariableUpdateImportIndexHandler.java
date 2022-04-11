/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE;

@RequiredArgsConstructor
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler
  extends TimestampBasedDataSourceImportIndexHandler<IngestedDataSourceDto> {

  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID = "externalVariableUpdateImportIndex";

  @Override
  public String getEngineAlias() {
    return ENGINE_ALIAS_OPTIMIZE;
  }

  @Override
  protected String getElasticsearchDocID() {
    return EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }

  @Override
  protected IngestedDataSourceDto getDataSource() {
    return new IngestedDataSourceDto(getEngineAlias());
  }

}
