/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.security;

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

}
