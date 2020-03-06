/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event.handler;

import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESSING_ENGINE_REFERENCE;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;


@Component
public class ExternalEventTraceImportIndexHandler extends TimestampBasedImportIndexHandler {

  private static final String EXTERNAL_EVENT_TRACE_IMPORT_INDEX_DOC_ID =
    EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX;

  @Override
  protected String getElasticsearchDocID() {
    return EXTERNAL_EVENT_TRACE_IMPORT_INDEX_DOC_ID;
  }

  @Override
  public String getEngineAlias() {
    return EVENT_PROCESSING_ENGINE_REFERENCE;
  }
}
