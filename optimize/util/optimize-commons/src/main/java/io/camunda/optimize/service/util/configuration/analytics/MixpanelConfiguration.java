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
    final int PRIME = 59;
    int result = 1;
    final Object $apiHost = getApiHost();
    result = result * PRIME + ($apiHost == null ? 43 : $apiHost.hashCode());
    final Object $importPath = getImportPath();
    result = result * PRIME + ($importPath == null ? 43 : $importPath.hashCode());
    final Object $token = getToken();
    result = result * PRIME + ($token == null ? 43 : $token.hashCode());
    final Object $projectId = getProjectId();
    result = result * PRIME + ($projectId == null ? 43 : $projectId.hashCode());
    final Object $properties = getProperties();
    result = result * PRIME + ($properties == null ? 43 : $properties.hashCode());
    final Object $serviceAccount = getServiceAccount();
    result = result * PRIME + ($serviceAccount == null ? 43 : $serviceAccount.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelConfiguration)) {
      return false;
    }
    final MixpanelConfiguration other = (MixpanelConfiguration) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    final Object this$apiHost = getApiHost();
    final Object other$apiHost = other.getApiHost();
    if (this$apiHost == null ? other$apiHost != null : !this$apiHost.equals(other$apiHost)) {
      return false;
    }
    final Object this$importPath = getImportPath();
    final Object other$importPath = other.getImportPath();
    if (this$importPath == null
        ? other$importPath != null
        : !this$importPath.equals(other$importPath)) {
      return false;
    }
    final Object this$token = getToken();
    final Object other$token = other.getToken();
    if (this$token == null ? other$token != null : !this$token.equals(other$token)) {
      return false;
    }
    final Object this$projectId = getProjectId();
    final Object other$projectId = other.getProjectId();
    if (this$projectId == null
        ? other$projectId != null
        : !this$projectId.equals(other$projectId)) {
      return false;
    }
    final Object this$properties = getProperties();
    final Object other$properties = other.getProperties();
    if (this$properties == null
        ? other$properties != null
        : !this$properties.equals(other$properties)) {
      return false;
    }
    final Object this$serviceAccount = getServiceAccount();
    final Object other$serviceAccount = other.getServiceAccount();
    if (this$serviceAccount == null
        ? other$serviceAccount != null
        : !this$serviceAccount.equals(other$serviceAccount)) {
      return false;
    }
    return true;
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
      final int PRIME = 59;
      int result = 1;
      final Object $stage = getStage();
      result = result * PRIME + ($stage == null ? 43 : $stage.hashCode());
      final Object $organizationId = getOrganizationId();
      result = result * PRIME + ($organizationId == null ? 43 : $organizationId.hashCode());
      final Object $clusterId = getClusterId();
      result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof TrackingProperties)) {
        return false;
      }
      final TrackingProperties other = (TrackingProperties) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$stage = getStage();
      final Object other$stage = other.getStage();
      if (this$stage == null ? other$stage != null : !this$stage.equals(other$stage)) {
        return false;
      }
      final Object this$organizationId = getOrganizationId();
      final Object other$organizationId = other.getOrganizationId();
      if (this$organizationId == null
          ? other$organizationId != null
          : !this$organizationId.equals(other$organizationId)) {
        return false;
      }
      final Object this$clusterId = getClusterId();
      final Object other$clusterId = other.getClusterId();
      if (this$clusterId == null
          ? other$clusterId != null
          : !this$clusterId.equals(other$clusterId)) {
        return false;
      }
      return true;
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
      final int PRIME = 59;
      int result = 1;
      final Object $username = getUsername();
      result = result * PRIME + ($username == null ? 43 : $username.hashCode());
      final Object $secret = getSecret();
      result = result * PRIME + ($secret == null ? 43 : $secret.hashCode());
      return result;
    }

    @Override
    public boolean equals(final Object o) {
      if (o == this) {
        return true;
      }
      if (!(o instanceof ServiceAccount)) {
        return false;
      }
      final ServiceAccount other = (ServiceAccount) o;
      if (!other.canEqual((Object) this)) {
        return false;
      }
      final Object this$username = getUsername();
      final Object other$username = other.getUsername();
      if (this$username == null ? other$username != null : !this$username.equals(other$username)) {
        return false;
      }
      final Object this$secret = getSecret();
      final Object other$secret = other.getSecret();
      if (this$secret == null ? other$secret != null : !this$secret.equals(other$secret)) {
        return false;
      }
      return true;
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
