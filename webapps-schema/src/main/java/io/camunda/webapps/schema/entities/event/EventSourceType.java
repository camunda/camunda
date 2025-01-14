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

public enum EventSourceType {
  JOB,
  PROCESS_INSTANCE,
  INCIDENT,
  PROCESS_MESSAGE_SUBSCRIPTION,
  UNKNOWN,
  UNSPECIFIED;

  private static final Logger LOGGER = LoggerFactory.getLogger(EventSourceType.class);

  public static EventSourceType fromZeebeValueType(final String valueType) {
    if (valueType == null) {
      return UNSPECIFIED;
    }
    try {
      return EventSourceType.valueOf(valueType);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error(
          "Value type not found for value [{}]. UNKNOWN type will be assigned.", valueType);
      return UNKNOWN;
    }
  }
}
