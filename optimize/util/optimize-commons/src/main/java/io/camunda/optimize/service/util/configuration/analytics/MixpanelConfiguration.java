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
      final String apiHost,
      final String importPath,
      final String token,
      final String projectId,
      final TrackingProperties properties,
      final ServiceAccount serviceAccount) {
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
    return apiHost + importPath;
  }

  public String getApiHost() {
    return apiHost;
  }

  @JsonProperty("apiHost")
  public void setApiHost(final String apiHost) {
    this.apiHost = apiHost;
  }

  public String getImportPath() {
    return importPath;
  }

  @JsonProperty("importPath")
  public void setImportPath(final String importPath) {
    this.importPath = importPath;
  }

  public String getToken() {
    return token;
  }

  @JsonProperty("token")
  public void setToken(final String token) {
    this.token = token;
  }

  public String getProjectId() {
    return projectId;
  }

  @JsonProperty("projectId")
  public void setProjectId(final String projectId) {
    this.projectId = projectId;
  }

  public TrackingProperties getProperties() {
    return properties;
  }

  @JsonProperty("properties")
  public void setProperties(final TrackingProperties properties) {
    this.properties = properties;
  }

  public ServiceAccount getServiceAccount() {
    return serviceAccount;
  }

  @JsonProperty("serviceAccount")
  public void setServiceAccount(final ServiceAccount serviceAccount) {
    this.serviceAccount = serviceAccount;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelConfiguration;
  }

  @Override
  public int hashCode() {
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
  }

  @Override
  public String toString() {
    return "MixpanelConfiguration(apiHost="
        + getApiHost()
        + ", importPath="
        + getImportPath()
        + ", token="
        + getToken()
        + ", projectId="
        + getProjectId()
        + ", properties="
        + getProperties()
        + ", serviceAccount="
        + getServiceAccount()
        + ")";
  }

  public static class TrackingProperties {

    @JsonProperty("stage")
    private String stage;

    @JsonProperty("organizationId")
    private String organizationId;

    @JsonProperty("clusterId")
    private String clusterId;

    public TrackingProperties(
        final String stage, final String organizationId, final String clusterId) {
      this.stage = stage;
      this.organizationId = organizationId;
      this.clusterId = clusterId;
    }

    protected TrackingProperties() {}

    public String getStage() {
      return stage;
    }

    @JsonProperty("stage")
    public void setStage(final String stage) {
      this.stage = stage;
    }

    public String getOrganizationId() {
      return organizationId;
    }

    @JsonProperty("organizationId")
    public void setOrganizationId(final String organizationId) {
      this.organizationId = organizationId;
    }

    public String getClusterId() {
      return clusterId;
    }

    @JsonProperty("clusterId")
    public void setClusterId(final String clusterId) {
      this.clusterId = clusterId;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof TrackingProperties;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "MixpanelConfiguration.TrackingProperties(stage="
          + getStage()
          + ", organizationId="
          + getOrganizationId()
          + ", clusterId="
          + getClusterId()
          + ")";
    }
  }

  public static class ServiceAccount {

    @JsonProperty("username")
    private String username;

    @JsonProperty("secret")
    private String secret;

    public ServiceAccount(final String username, final String secret) {
      this.username = username;
      this.secret = secret;
    }

    protected ServiceAccount() {}

    public String getUsername() {
      return username;
    }

    @JsonProperty("username")
    public void setUsername(final String username) {
      this.username = username;
    }

    public String getSecret() {
      return secret;
    }

    @JsonProperty("secret")
    public void setSecret(final String secret) {
      this.secret = secret;
    }

    protected boolean canEqual(final Object other) {
      return other instanceof ServiceAccount;
    }

    @Override
    public int hashCode() {
      return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
    }

    @Override
    public boolean equals(final Object o) {
      return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
    }

    @Override
    public String toString() {
      return "MixpanelConfiguration.ServiceAccount(username="
          + getUsername()
          + ", secret="
          + getSecret()
          + ")";
    }
  }
}
