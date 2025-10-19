/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.schema.entities.messagesubscription;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public enum MessageSubscriptionState {
  CREATED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  RESOLVED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  SEQUENCE_FLOW_TAKEN,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ELEMENT_ACTIVATING,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ELEMENT_ACTIVATED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ELEMENT_COMPLETING,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ELEMENT_COMPLETED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ELEMENT_TERMINATED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  ACTIVATED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  COMPLETED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  TIMED_OUT,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  FAILED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  RETRIES_UPDATED,

  CORRELATED,
  DELETED,

  /**
   * @deprecated since 8.9 as it is not used by message subscriptions
   */
  @Deprecated
  CANCELED,

  MIGRATED,
  UNKNOWN;

  private static final Logger LOGGER = LoggerFactory.getLogger(MessageSubscriptionState.class);

  public static MessageSubscriptionState fromZeebeIntent(final String intent) {
    try {
      return MessageSubscriptionState.valueOf(intent);
    } catch (final IllegalArgumentException ex) {
      LOGGER.error("Event type not found for value [{}]. UNKNOWN type will be assigned.", intent);
      return UNKNOWN;
    }
  }
}
