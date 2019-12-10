/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.schema.index.events;

import org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex;

public class EventProcessInstanceIndex extends ProcessInstanceIndex {

  public static final String EVENT_PROCESS_INSTANCE_INDEX_PREFIX = "event-process-instance-";

  public EventProcessInstanceIndex(final String eventProcessId) {
    super(EVENT_PROCESS_INSTANCE_INDEX_PREFIX + eventProcessId);
  }
}
