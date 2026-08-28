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
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(ClientConfig.class);

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
  public boolean waitStatesEnabled;
  public boolean isNavV2Enabled;
  @Autowired private OperateProfileService profileService;
  @Autowired private EnvironmentService environmentService;
  @Autowired private OperateProperties operateProperties;
  @Autowired private CamundaSecurityLibraryProperties cslProperties;

  @Value("${camunda.data.wait-states.enabled:true}")
  private boolean waitStatesEnabledProperty;

  @Autowired private ServletContext context;

  @PostConstruct
  public void logNavV2Resolution() {
    final boolean isSaas = isSaas();
    LOGGER.debug(
        "Operate nav v2 resolved to {} (explicit override={}, isSaas={})",
        operateProperties.resolveNavV2Enabled(isSaas),
        operateProperties.getNavV2Enabled(),
        isSaas);
  }

  public String asJson() {
    isEnterprise = operateProperties.isEnterprise();
    clusterId = operateProperties.getCloud().getClusterId();
    organizationId = operateProperties.getCloud().getOrganizationId();
    mixpanelAPIHost = operateProperties.getCloud().getMixpanelAPIHost();
    mixpanelToken = operateProperties.getCloud().getMixpanelToken();
    contextPath = context.getContextPath();
    baseName = context.getContextPath() + "/operate";
    canLogout = cslProperties.getAuthentication().getOidc().getOrganizationId() == null;
    isLoginDelegated = profileService.isLoginDelegated();
    tasklistUrl = operateProperties.getTasklistUrl();
    resourcePermissionsEnabled = cslProperties.getAuthorizations().isEnabled();
    multiTenancyEnabled = cslProperties.getMultiTenancy().isChecksEnabled();
    databaseType = environmentService.getDatabaseType();
    waitStatesEnabled = waitStatesEnabledProperty;
    isNavV2Enabled = operateProperties.resolveNavV2Enabled(isSaas());
    try {
      return String.format(
          "window.clientConfig = %s;", new ObjectMapper().writeValueAsString(this));
    } catch (final JsonProcessingException e) {
      return "window.clientConfig = {};";
    }
  }

  private boolean isSaas() {
    return cslProperties.getSaas() != null && cslProperties.getSaas().getClusterId() != null;
  }
}
