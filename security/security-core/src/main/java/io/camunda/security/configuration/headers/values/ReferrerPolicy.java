/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.headers.values;

public enum ReferrerPolicy {
  NO_REFERRER,
  NO_REFERRER_WHEN_DOWNGRADE,
  SAME_ORIGIN,
  ORIGIN,
  STRICT_ORIGIN,
  ORIGIN_WHEN_CROSS_ORIGIN,
  STRICT_ORIGIN_WHEN_CROSS_ORIGIN,
  UNSAFE_URL
}
