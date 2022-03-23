/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.TimestampBasedEngineImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.PROCESS_INSTANCE_MULTI_ALIAS;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedProcessInstanceImportIndexHandler extends TimestampBasedEngineImportIndexHandler {

  public static final String COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID = PROCESS_INSTANCE_MULTI_ALIAS;

  private final EngineContext engineContext;

  public CompletedProcessInstanceImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected String getElasticsearchDocID() {
    return COMPLETED_PROCESS_INSTANCE_IMPORT_INDEX_DOC_ID;
  }
}
