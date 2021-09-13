/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.ingested.handler;

import org.camunda.optimize.service.importing.TimestampBasedIngestedDataImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.ENGINE_ALIAS_OPTIMIZE;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ExternalVariableUpdateImportIndexHandler extends TimestampBasedIngestedDataImportIndexHandler {
  public static final String EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID = "externalVariableUpdateImportIndex";

  @Override
  public String getEngineAlias() {
    return ENGINE_ALIAS_OPTIMIZE;
  }

  @Override
  protected String getElasticsearchDocId() {
    return EXTERNAL_VARIABLE_UPDATE_IMPORT_INDEX_DOC_ID;
  }
}
