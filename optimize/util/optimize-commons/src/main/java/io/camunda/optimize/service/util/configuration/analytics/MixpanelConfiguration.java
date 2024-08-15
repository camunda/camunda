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
    return apiHost + importPath;
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
