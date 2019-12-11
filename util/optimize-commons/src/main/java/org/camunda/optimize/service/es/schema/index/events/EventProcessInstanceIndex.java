/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.EVENT_PROCESS_INSTANCE_INDEX_PREFIX;

public class EventProcessInstanceIndex extends ProcessInstanceIndex {

  public EventProcessInstanceIndex(final String eventProcessId) {
    super(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessId);
  }
}
