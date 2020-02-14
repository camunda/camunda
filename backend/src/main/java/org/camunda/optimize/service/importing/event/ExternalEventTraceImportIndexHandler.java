/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.event;

import org.camunda.optimize.service.importing.TimestampBasedImportIndexHandler;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.springframework.stereotype.Component;


@Component
public class ExternalEventTraceImportIndexHandler extends TimestampBasedImportIndexHandler {

  public static final String EXTERNAL_EVENT_TRACE_IMPORT_INDEX_DOC_ID =
    ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE;

  @Override
  protected String getElasticsearchDocID() {
    return EXTERNAL_EVENT_TRACE_IMPORT_INDEX_DOC_ID;
  }

  @Override
  public String getEngineAlias() {
    return ElasticsearchConstants.EVENT_PROCESSING_ENGINE_REFERENCE;
  }
}
