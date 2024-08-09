/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.event.handler;

import static io.camunda.optimize.service.db.DatabaseConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static io.camunda.optimize.service.db.DatabaseConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

import io.camunda.optimize.service.importing.TimestampBasedEventDataImportIndexHandler;
import org.springframework.stereotype.Component;

@Component
public class ExternalEventTraceImportIndexHandler
    extends TimestampBasedEventDataImportIndexHandler {

  @Override
  protected String getDatabaseDocID() {
    return EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX;
  }
}
