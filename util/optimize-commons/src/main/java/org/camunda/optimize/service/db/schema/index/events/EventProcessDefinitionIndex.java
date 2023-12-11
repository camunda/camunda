/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.db.schema.index.events;

import org.camunda.optimize.service.db.DatabaseConstants;
import org.camunda.optimize.service.db.schema.index.ProcessDefinitionIndex;

public abstract class EventProcessDefinitionIndex<TBuilder> extends ProcessDefinitionIndex<TBuilder> {

  public static final int VERSION = 5;

  @Override
  public String getIndexName() {
    return DatabaseConstants.EVENT_PROCESS_DEFINITION_INDEX_NAME;
  }

  @Override
  public int getVersion() {
    return VERSION;
  }

}
