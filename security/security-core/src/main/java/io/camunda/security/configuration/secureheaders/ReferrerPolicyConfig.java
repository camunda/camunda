/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

import io.camunda.security.configuration.secureheaders.values.ReferrerPolicy;

public class ReferrerPolicyConfig {
  private ReferrerPolicy referrerPolicy = ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN;

  public ReferrerPolicy getReferrerPolicy() {
    return referrerPolicy;
  }

  public void setReferrerPolicy(final ReferrerPolicy referrerPolicy) {
    this.referrerPolicy = referrerPolicy;
  }
}
