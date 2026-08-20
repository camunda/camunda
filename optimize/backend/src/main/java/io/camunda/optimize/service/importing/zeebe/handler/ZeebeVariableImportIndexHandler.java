/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.zeebe.handler;

import io.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import io.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeVariableImportIndexHandler extends PositionBasedImportIndexHandler {

  private static final String ZEEBE_VAR_IMPORT_INDEX_DOC_ID = "zeebeVariableImportIndex";

  public ZeebeVariableImportIndexHandler(final ZeebeDataSourceDto dataSourceDto) {
    this.dataSource = dataSourceDto;
  }

  @Override
  protected String getDatabaseDocID() {
    return ZEEBE_VAR_IMPORT_INDEX_DOC_ID;
  }
}
