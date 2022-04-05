/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.property;

public class OAuthClientProperties {

  public static final String DEFAULT_AUDIENCE = "operate.camunda.io";

  private String audience = DEFAULT_AUDIENCE;
  private String clusterId;

  private String scope;

  public String getClusterId() {
    return clusterId;
  }

  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  public String getAudience() {
    return audience;
  }

  public void setAudience(final String audience) {
    this.audience = audience;
  }

  public String getScope() {
    if (scope == null || scope.isEmpty()){
      return clusterId;
    }
    return scope;
  }

  public void setScope(final String scope) {
    this.scope = scope;
  }

}
