/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util.configuration.tracking;

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
  @JsonProperty("project-id")
  private String projectId;
  @JsonProperty("properties")
  private TrackingProperties properties;
  @JsonProperty("service-account")
  private ServiceAccount serviceAccount;

  @JsonIgnore
  public String getImportUrl() {
    return this.apiHost + this.importPath;
  }

  @AllArgsConstructor
  @Data
  @NoArgsConstructor(access = AccessLevel.PROTECTED)
  public static class TrackingProperties {
    @JsonProperty("organizationId")
    private String organizationId;
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
