/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.mixpanel.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class MixpanelEventProperties {

  // Mixpanel default properties, see
  // https://developer.mixpanel.com/reference/import-events#high-level-requirements
  @JsonProperty("time")
  private long time = System.currentTimeMillis();

  // defaults to empty string which equals "no user association"
  // see https://developer.mixpanel.com/reference/import-events#propertiesdistinct_id
  @JsonProperty("distinct_id")
  private String distinctId = "";

  @JsonProperty("$insert_id")
  private String insertId = UUID.randomUUID().toString();

  // Custom properties
  @JsonProperty("product")
  private String product = "optimize";

  @JsonProperty("orgId")
  private String organizationId;

  @JsonProperty("stage")
  private String stage;

  @JsonProperty("userId")
  private String userId;

  @JsonProperty("clusterId")
  private String clusterId;

  public MixpanelEventProperties(
      final String stage, final String organizationId, final String clusterId) {
    this.stage = stage;
    this.organizationId = organizationId;
    this.clusterId = clusterId;
  }

  public MixpanelEventProperties() {}

  @JsonProperty("org_id")
  public String getOrgGroupKey() {
    return organizationId;
  }

  @JsonProperty("cluster_id")
  public String getOrgClusterId() {
    return clusterId;
  }

  public long getTime() {
    return time;
  }

  @JsonProperty("time")
  public void setTime(final long time) {
    this.time = time;
  }

  public String getDistinctId() {
    return distinctId;
  }

  @JsonProperty("distinct_id")
  public void setDistinctId(final String distinctId) {
    this.distinctId = distinctId;
  }

  public String getInsertId() {
    return insertId;
  }

  @JsonProperty("$insert_id")
  public void setInsertId(final String insertId) {
    this.insertId = insertId;
  }

  public String getProduct() {
    return product;
  }

  @JsonProperty("product")
  public void setProduct(final String product) {
    this.product = product;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  @JsonProperty("orgId")
  public void setOrganizationId(final String organizationId) {
    this.organizationId = organizationId;
  }

  public String getStage() {
    return stage;
  }

  @JsonProperty("stage")
  public void setStage(final String stage) {
    this.stage = stage;
  }

  public String getUserId() {
    return userId;
  }

  @JsonProperty("userId")
  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getClusterId() {
    return clusterId;
  }

  @JsonProperty("clusterId")
  public void setClusterId(final String clusterId) {
    this.clusterId = clusterId;
  }

  protected boolean canEqual(final Object other) {
    return other instanceof MixpanelEventProperties;
  }

  @Override
  public int hashCode() {
    final int PRIME = 59;
    int result = 1;
    final long $time = getTime();
    result = result * PRIME + (int) ($time >>> 32 ^ $time);
    final Object $distinctId = getDistinctId();
    result = result * PRIME + ($distinctId == null ? 43 : $distinctId.hashCode());
    final Object $insertId = getInsertId();
    result = result * PRIME + ($insertId == null ? 43 : $insertId.hashCode());
    final Object $product = getProduct();
    result = result * PRIME + ($product == null ? 43 : $product.hashCode());
    final Object $organizationId = getOrganizationId();
    result = result * PRIME + ($organizationId == null ? 43 : $organizationId.hashCode());
    final Object $stage = getStage();
    result = result * PRIME + ($stage == null ? 43 : $stage.hashCode());
    final Object $userId = getUserId();
    result = result * PRIME + ($userId == null ? 43 : $userId.hashCode());
    final Object $clusterId = getClusterId();
    result = result * PRIME + ($clusterId == null ? 43 : $clusterId.hashCode());
    return result;
  }

  @Override
  public boolean equals(final Object o) {
    if (o == this) {
      return true;
    }
    if (!(o instanceof MixpanelEventProperties)) {
      return false;
    }
    final MixpanelEventProperties other = (MixpanelEventProperties) o;
    if (!other.canEqual((Object) this)) {
      return false;
    }
    if (getTime() != other.getTime()) {
      return false;
    }
    final Object this$distinctId = getDistinctId();
    final Object other$distinctId = other.getDistinctId();
    if (this$distinctId == null
        ? other$distinctId != null
        : !this$distinctId.equals(other$distinctId)) {
      return false;
    }
    final Object this$insertId = getInsertId();
    final Object other$insertId = other.getInsertId();
    if (this$insertId == null ? other$insertId != null : !this$insertId.equals(other$insertId)) {
      return false;
    }
    final Object this$product = getProduct();
    final Object other$product = other.getProduct();
    if (this$product == null ? other$product != null : !this$product.equals(other$product)) {
      return false;
    }
    final Object this$organizationId = getOrganizationId();
    final Object other$organizationId = other.getOrganizationId();
    if (this$organizationId == null
        ? other$organizationId != null
        : !this$organizationId.equals(other$organizationId)) {
      return false;
    }
    final Object this$stage = getStage();
    final Object other$stage = other.getStage();
    if (this$stage == null ? other$stage != null : !this$stage.equals(other$stage)) {
      return false;
    }
    final Object this$userId = getUserId();
    final Object other$userId = other.getUserId();
    if (this$userId == null ? other$userId != null : !this$userId.equals(other$userId)) {
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
    return "MixpanelEventProperties(time="
        + getTime()
        + ", distinctId="
        + getDistinctId()
        + ", insertId="
        + getInsertId()
        + ", product="
        + getProduct()
        + ", organizationId="
        + getOrganizationId()
        + ", stage="
        + getStage()
        + ", userId="
        + getUserId()
        + ", clusterId="
        + getClusterId()
        + ")";
  }
}
