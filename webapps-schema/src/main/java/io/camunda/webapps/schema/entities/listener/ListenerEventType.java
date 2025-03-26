/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ListenerEventType {
  START,
  END,
  COMPLETING,
  ASSIGNING,
  UPDATING,
  UNKNOWN,
  UNSPECIFIED;

  private static final Logger LOGGER = LoggerFactory.getLogger(ListenerEventType.class);

  public static ListenerEventType fromZeebeListenerEventType(final String listenerEventType) {
    if (listenerEventType == null) {
      LOGGER.debug("Listener event type is null. Setting it as {}.", UNSPECIFIED);
      return UNSPECIFIED;
    }
    try {
      return ListenerEventType.valueOf(listenerEventType);
    } catch (final IllegalArgumentException e) {
      LOGGER.debug(
          "Unknown listener event type [{}]. Setting it as {}.", listenerEventType, UNKNOWN);
    }
    return UNKNOWN;
  }
}
