/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.service.es.schema.index.ProcessDefinitionIndex;
import org.camunda.optimize.upgrade.es.ElasticsearchConstants;

public class EventProcessDefinitionIndex extends ProcessDefinitionIndex {

  public static final int VERSION = 4;

  @Override
  public String getIndexName() {
    return ElasticsearchConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

}
