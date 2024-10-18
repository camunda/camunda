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
    return org.apache.commons.lang3.builder.HashCodeBuilder.reflectionHashCode(this);
  }

  @Override
  public boolean equals(final Object o) {
    return org.apache.commons.lang3.builder.EqualsBuilder.reflectionEquals(this, o);
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
