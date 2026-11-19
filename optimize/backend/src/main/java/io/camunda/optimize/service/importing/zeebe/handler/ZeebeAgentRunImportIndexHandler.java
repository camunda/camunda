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
 * Import position of the flat agent run import. It reads the same records as the Phase 1 agent
 * instance import but keeps its own position, so each import can be held back or reset without
 * moving the other.
 */
@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class ZeebeAgentRunImportIndexHandler extends PositionBasedImportIndexHandler {

  private static final String ZEEBE_AGENT_RUN_IMPORT_INDEX_DOC_ID = "zeebeAgentRunImportIndex";

  public ZeebeAgentRunImportIndexHandler(final ZeebeDataSourceDto dataSourceDto) {
    this.dataSource = dataSourceDto;
  }

  @Override
  protected String getDatabaseDocID() {
    return ZEEBE_AGENT_RUN_IMPORT_INDEX_DOC_ID;
  }
}
