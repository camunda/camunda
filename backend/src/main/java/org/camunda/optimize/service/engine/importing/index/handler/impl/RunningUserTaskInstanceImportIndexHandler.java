/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.index.handler.impl;

import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.index.handler.TimestampBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class RunningUserTaskInstanceImportIndexHandler extends TimestampBasedImportIndexHandler {

  private static final String RUNNING_USER_TASK_INSTANCE_IMPORT_INDEX_DOC_ID = "runningUserTaskInstanceImportIndex";

  public RunningUserTaskInstanceImportIndexHandler(EngineContext engineContext) {
    super(engineContext);
  }

  @Override
  protected String getElasticsearchDocID() {
    return RUNNING_USER_TASK_INSTANCE_IMPORT_INDEX_DOC_ID;
  }

}
