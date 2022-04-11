/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.handler;

import org.camunda.optimize.service.importing.TimestampBasedEventDataImportIndexHandler;
import org.springframework.stereotype.Component;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EXTERNAL_EVENTS_INDEX_SUFFIX;

@Component
public class ExternalEventTraceImportIndexHandler extends TimestampBasedEventDataImportIndexHandler {

  @Override
  protected String getElasticsearchDocID() {
    return EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + EXTERNAL_EVENTS_INDEX_SUFFIX;
  }
}
