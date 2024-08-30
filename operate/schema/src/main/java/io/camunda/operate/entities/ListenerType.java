/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum ListenerType {
  EXECUTION_LISTENER,
  TASK_LISTENER,
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(ListenerType.class);

  public static ListenerType fromZeebeJobKind(final String jobKind) {
    if (jobKind == null) {
      LOGGER.warn("Job kind is null. UNKNOWN type will be assigned.", jobKind);
      return UNKNOWN;
    }
    try {
      return ListenerType.valueOf(jobKind);
    } catch (final IllegalArgumentException ex) {
      LOGGER.warn("Job kind not found for value [{}]. UNKNOWN type will be assigned.", jobKind);
      return UNKNOWN;
    }
  }
}
