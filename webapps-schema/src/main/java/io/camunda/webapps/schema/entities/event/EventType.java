/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventType {
  CREATED,

  RESOLVED,

  SEQUENCE_FLOW_TAKEN,

  ELEMENT_ACTIVATING,
  ELEMENT_ACTIVATED,
  ELEMENT_COMPLETING,
  ELEMENT_COMPLETED,
  ELEMENT_TERMINATED,

  // JOB
  ACTIVATED,

  COMPLETED,

  TIMED_OUT,

  FAILED,

  RETRIES_UPDATED,

  // MESSAGE
  CORRELATED,

  CANCELED,

  MIGRATED,
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(EventType.class);

  public static EventType fromZeebeIntent(final String intent) {
    try {
      return EventType.valueOf(intent);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error("Event type not found for value [{}]. UNKNOWN type will be assigned.", intent);
      return UNKNOWN;
    }
  }
}
