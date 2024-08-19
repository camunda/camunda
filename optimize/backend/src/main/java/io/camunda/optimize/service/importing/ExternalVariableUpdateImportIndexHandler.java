/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing;

import static io.camunda.optimize.service.db.DatabaseConstants.ENGINE_ALIAS_OPTIMIZE;

import io.camunda.optimize.dto.optimize.datasource.IngestedDataSourceDto;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler
    extends TimestampBasedDataSourceImportIndexHandler<IngestedDataSourceDto> {

  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID =
      "externalVariableUpdateImportIndex";

  public ExternalVariableUpdateImportIndexHandler() {}

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
