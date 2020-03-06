/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.handler;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class CompletedUserTaskInstanceImportIndexHandler extends TimestampBasedImportIndexHandler {

  private static final String COMPLETED_USER_TASK_INSTANCE_IMPORT_INDEX_DOC_ID = "completedUserTaskInstanceImportIndex";

  private final EngineContext engineContext;

  public CompletedUserTaskInstanceImportIndexHandler(final EngineContext engineContext) {
    this.engineContext = engineContext;
  }

  @Override
  public String getEngineAlias() {
    return engineContext.getEngineAlias();
  }

  @Override
  protected String getElasticsearchDocID() {
    return COMPLETED_USER_TASK_INSTANCE_IMPORT_INDEX_DOC_ID;
  }

}
