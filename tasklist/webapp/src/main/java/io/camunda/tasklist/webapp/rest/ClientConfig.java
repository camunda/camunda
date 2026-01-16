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
  public boolean isLogoutCorsEnabled;
  public boolean isMultiTenancyEnabled;
  public boolean canLogout;
  public boolean isLoginDelegated;
  public String contextPath;
  public String baseName;
  public String clientMode;
  public String databaseType;

  // Cloud related properties for mixpanel events
  @Value("${CAMUNDA_TASKLIST_CLOUD_ORGANIZATIONID:#{null}}")
  public String organizationId;

  @Value("${CAMUNDA_TASKLIST_CLOUD_CLUSTERID:#{null}}")
  public String clusterId;

  @Value("${CAMUNDA_TASKLIST_CLOUD_STAGE:#{null}}")
  public String stage;

  @Value("${CAMUNDA_TASKLIST_CLOUD_MIXPANELTOKEN:#{null}}")
  public String mixpanelToken;

  @Value("${CAMUNDA_TASKLIST_CLOUD_MIXPANELAPIHOST:#{null}}")
  public String mixpanelAPIHost;

  @Value("${CAMUNDA_TASKLIST_IDENTITY_RESOURCE_PERMISSIONS_ENABLED:#{false}}")
  public boolean isResourcePermissionsEnabled;

  @Value("${CAMUNDA_TASKLIST_IDENTITY_USER_ACCESS_RESTRICTIONS_ENABLED:#{true}}")
  public boolean isUserAccessRestrictionsEnabled;

  public long maxRequestSize;

  @Value("${spring.servlet.multipart.max-request-size:4MB}")
  private DataSize maxRequestSizeConfigValue;

  @Value("${CAMUNDA_TASKLIST_V2_MODE_ENABLED:#{true}}")
  private boolean isV2ModeEnabled;

  @Autowired private TasklistProfileService profileService;
  @Autowired private EnvironmentService environmentService;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private SecurityConfiguration securityConfiguration;
  @Autowired private ServletContext context;

  @PostConstruct
  public void init() {
    isEnterprise = tasklistProperties.isEnterprise();
    isLogoutCorsEnabled = securityConfiguration.getAuthentication().getLogoutCorsEnabled();
    isMultiTenancyEnabled = securityConfiguration.getMultiTenancy().isChecksEnabled();
    contextPath = context.getContextPath();
    baseName = context.getContextPath() + "/tasklist";
    canLogout = profileService.currentProfileCanLogout();
    isLoginDelegated = profileService.isLoginDelegated();
    maxRequestSize = maxRequestSizeConfigValue.toBytes();
    clientMode = isV2ModeEnabled ? "v2" : "v1";
    databaseType = environmentService.getDatabaseType();
  }
}
