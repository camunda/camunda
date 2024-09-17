/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.dto.optimize.query.ui_configuration;

import lombok.Data;

@Data
public class MixpanelConfigResponseDto {

  private boolean enabled;
  private String apiHost;
  private String token;
  private String organizationId;
  private String osanoScriptUrl;
  private String stage;
  private String clusterId;

  public MixpanelConfigResponseDto(
      boolean enabled,
      String apiHost,
      String token,
      String organizationId,
      String osanoScriptUrl,
      String stage,
      String clusterId) {
    this.enabled = enabled;
    this.apiHost = apiHost;
    this.token = token;
    this.organizationId = organizationId;
    this.osanoScriptUrl = osanoScriptUrl;
    this.stage = stage;
    this.clusterId = clusterId;
  }

  public MixpanelConfigResponseDto() {}
}
