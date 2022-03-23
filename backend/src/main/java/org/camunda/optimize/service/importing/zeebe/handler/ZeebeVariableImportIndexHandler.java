/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.zeebe.handler;

import org.camunda.optimize.dto.optimize.datasource.ZeebeDataSourceDto;
import org.camunda.optimize.service.importing.PositionBasedImportIndexHandler;
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
  protected String getElasticsearchDocID() {
    return ZEEBE_VAR_IMPORT_INDEX_DOC_ID;
  }

}
