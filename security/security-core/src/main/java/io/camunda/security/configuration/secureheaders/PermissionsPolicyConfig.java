/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration.secureheaders;

/** Sets the 'Permissions-Policy' header. Not set by default. */
public class PermissionsPolicyConfig {

  /**
   * Set policy as plain text, e.g. 'picture-in-picture=(), geolocation=(self https://example.com/),
   * camera=*'.
   */
  private String policy;

  public String getPolicy() {
    return policy;
  }

  public void setPolicy(final String policy) {
    this.policy = policy;
  }
}
