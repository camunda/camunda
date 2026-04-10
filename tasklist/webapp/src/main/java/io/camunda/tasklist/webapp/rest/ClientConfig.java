/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest;

import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import io.camunda.tasklist.webapp.service.EnvironmentService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Component
public class ClientConfig {

  public boolean isEnterprise;
  public boolean isMultiTenancyEnabled;
  public boolean canLogout;
  public boolean isLoginDelegated;
  public String contextPath;
  public String baseName;
  public String clientMode;
  public String databaseType;

  // Cloud related properties for mixpanel events
  public String organizationId;
  public String clusterId;
  public String stage;
  public String mixpanelToken;
  public String mixpanelAPIHost;

  public long maxRequestSize;

  @Value("${spring.servlet.multipart.max-request-size:4MB}")
  private DataSize maxRequestSizeConfigValue;

  @Value("${camunda.tasklist.v2-mode-enabled:#{true}}")
  private boolean isV2ModeEnabled;

  @Autowired private TasklistProfileService profileService;
  @Autowired private EnvironmentService environmentService;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SecurityConfiguration securityConfiguration;
  @Autowired private ServletContext context;

  @PostConstruct
  public void init() {
    isEnterprise = tasklistProperties.isEnterprise();
    isMultiTenancyEnabled = securityConfiguration.getMultiTenancy().isChecksEnabled();
    contextPath = context.getContextPath();
    baseName = context.getContextPath() + "/tasklist";
    canLogout = profileService.currentProfileCanLogout();
    isLoginDelegated = profileService.isLoginDelegated();
    maxRequestSize = maxRequestSizeConfigValue.toBytes();
    clientMode = isV2ModeEnabled ? "v2" : "v1";
    databaseType = environmentService.getDatabaseType();
    organizationId = tasklistProperties.getCloud().getOrganizationId();
    clusterId = tasklistProperties.getCloud().getClusterId();
    stage = tasklistProperties.getCloud().getStage();
    mixpanelToken = tasklistProperties.getCloud().getMixpanelToken();
    mixpanelAPIHost = tasklistProperties.getCloud().getMixpanelAPIHost();
  }
}
