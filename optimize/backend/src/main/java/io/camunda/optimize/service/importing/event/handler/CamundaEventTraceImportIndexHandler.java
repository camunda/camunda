/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.handler;

import io.camunda.optimize.service.db.DatabaseConstants;
import io.camunda.optimize.service.importing.TimestampBasedEventDataImportIndexHandler;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AllArgsConstructor
public class CamundaEventTraceImportIndexHandler extends TimestampBasedEventDataImportIndexHandler {

  private final String definitionKey;

  @Override
  protected String getDatabaseDocID() {
    return DatabaseConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey;
  }
}
