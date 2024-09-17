/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration.analytics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
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

  public MixpanelConfiguration(
      String apiHost,
      String importPath,
      String token,
      String projectId,
      TrackingProperties properties,
      ServiceAccount serviceAccount) {
    this.apiHost = apiHost;
    this.importPath = importPath;
    this.token = token;
    this.projectId = projectId;
    this.properties = properties;
    this.serviceAccount = serviceAccount;
  }

  protected MixpanelConfiguration() {}

  @JsonIgnore
  public String getImportUrl() {
    return this.apiHost + this.importPath;
  }

  @Data
  public static class TrackingProperties {

    @JsonProperty("stage")
    private String stage;

    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("clusterId")
    private String clusterId;

    public TrackingProperties(String stage, String organizationId, String clusterId) {
      this.stage = stage;
      this.organizationId = organizationId;
      this.clusterId = clusterId;
    }

    protected TrackingProperties() {}
  }

  @Data
  public static class ServiceAccount {

    @JsonProperty("username")
    private String username;

    @JsonProperty("secret")
    private String secret;

    public ServiceAccount(String username, String secret) {
      this.username = username;
      this.secret = secret;
    }

    protected ServiceAccount() {}
  }
}
