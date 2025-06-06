/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.ReferrerPolicy;

/**
 * Sets the 'Referrer-Policy' header. Can take the following values:
 *
 * <ul>
 *   <li>NO_REFERRER
 *   <li>NO_REFERRER_WHEN_DOWNGRADE
 *   <li>SAME_ORIGIN
 *   <li>ORIGIN
 *   <li>STRICT_ORIGIN
 *   <li>ORIGIN_WHEN_CROSS_ORIGIN
 *   <li>STRICT_ORIGIN_WHEN_CROSS_ORIGIN
 *   <li>UNSAFE_URL
 * </ul>
 *
 * By default, the value is STRICT_ORIGIN_WHEN_CROSS_ORIGIN.
 */
public class ReferrerPolicyConfig {
  private ReferrerPolicy referrerPolicy = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

  public ReferrerPolicy getReferrerPolicy() {
    return referrerPolicy;
  }

  public void setReferrerPolicy(final ReferrerPolicy referrerPolicy) {
    this.referrerPolicy = referrerPolicy;
  }
}
