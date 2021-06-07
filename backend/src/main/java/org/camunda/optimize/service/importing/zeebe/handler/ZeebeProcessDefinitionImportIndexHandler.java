/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.zeebe.handler;

import org.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeProcessDefinitionImportIndexHandler extends PositionBasedImportIndexHandler {

  private static final String ZEEBE_PROC_DEF_IMPORT_INDEX_DOC_ID = "zeebeProcessDefinitionImportIndex";

  public ZeebeProcessDefinitionImportIndexHandler(final int partitionId) {
    this.partitionId = partitionId;
  }

  @Override
  protected String getElasticsearchDocID() {
    return ZEEBE_PROC_DEF_IMPORT_INDEX_DOC_ID;
  }

}
