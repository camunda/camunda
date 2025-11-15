/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.EnvironmentService;
import io.camunda.operate.OperateProfileService;
import io.camunda.operate.property.OperateProperties;
import io.camunda.security.configuration.SecurityConfiguration;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientConfig {

  public boolean isEnterprise;
  public boolean canLogout;
  public String contextPath;
  public String baseName;
  public String organizationId;
  public String clusterId;
  public String mixpanelAPIHost;
  public String mixpanelToken;
  public boolean isLoginDelegated;
  public String tasklistUrl;
  public boolean resourcePermissionsEnabled;
  public boolean multiTenancyEnabled;
  public String databaseType;
  @Autowired private OperateProfileService profileService;
  @Autowired private EnvironmentService environmentService;
  @Autowired private OperateProperties operateProperties;
  @Autowired private SecurityConfiguration securityConfiguration;
  @Autowired private ServletContext context;

  public String asJson() {
    isEnterprise = operateProperties.isEnterprise();
    clusterId = operateProperties.getCloud().getClusterId();
    organizationId = operateProperties.getCloud().getOrganizationId();
    mixpanelAPIHost = operateProperties.getCloud().getMixpanelAPIHost();
    mixpanelToken = operateProperties.getCloud().getMixpanelToken();
    contextPath = context.getContextPath();
    baseName = context.getContextPath() + "/operate";
    // when operating in SaaS we don't have the logout option.
    canLogout = securityConfiguration.getAuthentication().getOidc().getOrganizationId() == null;
    isLoginDelegated = profileService.isLoginDelegated();
    tasklistUrl = operateProperties.getTasklistUrl();
    resourcePermissionsEnabled = securityConfiguration.getAuthorizations().isEnabled();
    multiTenancyEnabled = securityConfiguration.getMultiTenancy().isChecksEnabled();
    databaseType = environmentService.getDatabaseType();
    try {
      return String.format(
          "window.clientConfig = %s;", new ObjectMapper().writeValueAsString(this));
    } catch (final JsonProcessingException e) {
      return "window.clientConfig = {};";
    }
  }
}
