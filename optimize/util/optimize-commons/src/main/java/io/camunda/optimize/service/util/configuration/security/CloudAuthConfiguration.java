/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.security;

import java.util.Optional;
import lombok.Data;

@Data
public class CloudAuthConfiguration {
  // oauth client id to use by Optimize
  private String clientId;
  // oauth client secret to use by Optimize
  private String clientSecret;
  // the Auth0 tenant domain
  private String domain;
  // the Auth0 custom domain used on a tenant (usually used for the login page)
  private String customDomain;
  // the OpenIdConnect attribute where the user id is stored
  private String userIdAttributeName;
  // the name of the claim where the special organizations property is stored
  private String organizationClaimName;
  // the organization id this Optimize instance belongs to
  private String organizationId;
  // the id of the cluster Optimize belongs to
  private String clusterId;
  // the audience (scope) for API access
  private String audience;
  // the audience requested for a users service access token
  private String userAccessTokenAudience;
  // URL to request access tokens
  private String tokenUrl;

  public Optional<String> getUserAccessTokenAudience() {
    return Optional.ofNullable(userAccessTokenAudience);
  }
}
