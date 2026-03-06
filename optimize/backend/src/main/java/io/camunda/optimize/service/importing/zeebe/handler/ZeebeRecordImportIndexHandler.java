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

/**
 * Import index handler for the combined Zeebe record index. Tracks the last imported position and
 * sequence across <em>all</em> value types, replacing the four separate per-type handlers for the
 * combined-index import pipeline.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeRecordImportIndexHandler extends PositionBasedImportIndexHandler {

  private static final String ZEEBE_RECORD_IMPORT_INDEX_DOC_ID = "zeebeRecordImportIndex";

  public ZeebeRecordImportIndexHandler(final ZeebeDataSourceDto dataSourceDto) {
    this.dataSource = dataSourceDto;
  }

  @Override
  protected String getDatabaseDocID() {
    return ZEEBE_RECORD_IMPORT_INDEX_DOC_ID;
  }
}
