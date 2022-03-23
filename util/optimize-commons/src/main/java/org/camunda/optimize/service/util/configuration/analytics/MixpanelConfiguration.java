/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@Data
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MixpanelConfiguration {
  @JsonProperty("apiHost")
  private String apiHost;
  @JsonProperty("importPath")
  private String importPath;
  @JsonProperty("token")
  private String token;
  @JsonProperty("projectId")
  private String projectId;
  @JsonProperty("properties")
  private TrackingProperties properties;
  @JsonProperty("serviceAccount")
  private ServiceAccount serviceAccount;

  @JsonIgnore
  public String getImportUrl() {
    return this.apiHost + this.importPath;
  }

  @AllArgsConstructor
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class TrackingProperties {
    @JsonProperty("stage")
    private String stage;
    @JsonProperty("organizationId")
    private String organizationId;
    @JsonProperty("clusterId")
    private String clusterId;
  }

  @AllArgsConstructor
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class ServiceAccount {
    @JsonProperty("username")
    private String username;
    @JsonProperty("secret")
    private String secret;
  }
}
