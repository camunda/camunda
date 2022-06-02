/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum EventSourceType {

  JOB,
  PROCESS_INSTANCE,
  INCIDENT,
  PROCESS_MESSAGE_SUBSCRIPTION,
  UNKNOWN,
  UNSPECIFIED;

  private static final Logger logger = LoggerFactory.getLogger(EventSourceType.class);

  public static EventSourceType fromZeebeValueType(String valueType) {
    if (valueType == null) {
      return UNSPECIFIED;
    }
    try {
      return EventSourceType.valueOf(valueType);
    } catch (IllegalArgumentException ex) {
      logger.error("Value type not found for value [{}]. UNKNOWN type will be assigned.", valueType);
      return UNKNOWN;
    }
  }

}
