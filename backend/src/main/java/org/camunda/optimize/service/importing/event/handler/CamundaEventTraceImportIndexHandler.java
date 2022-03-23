/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.event.handler;

import lombok.AllArgsConstructor;
import org.camunda.optimize.service.importing.TimestampBasedEventDataImportIndexHandler;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@AllArgsConstructor
public class CamundaEventTraceImportIndexHandler extends TimestampBasedEventDataImportIndexHandler {

  private final String definitionKey;

  @Override
  protected String getElasticsearchDocID() {
    return ElasticsearchConstants.EVENT_PROCESSING_IMPORT_REFERENCE_PREFIX + definitionKey;
  }
}
