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
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
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

  @JsonProperty("org_id")
  public String getOrgGroupKey() {
    return organizationId;
  }

  @JsonProperty("cluster_id")
  public String getOrgClusterId() {
    return clusterId;
  }
}
