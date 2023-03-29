/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.webapp.security.OperateProfileService;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientConfig {

  @Autowired
  private OperateProfileService profileService;
  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ServletContext context;

  public boolean isEnterprise;

  public boolean canLogout;

  public String contextPath;

  public String organizationId;

  public String clusterId;

  public String mixpanelAPIHost;

  public String mixpanelToken;

  public boolean isLoginDelegated;

  public String tasklistUrl;

  public boolean resourcePermissionsEnabled;

  public String asJson(){
    isEnterprise = operateProperties.isEnterprise();
    clusterId = operateProperties.getCloud().getClusterId();
    organizationId = operateProperties.getCloud().getOrganizationId();
    mixpanelAPIHost = operateProperties.getCloud().getMixpanelAPIHost();
    mixpanelToken = operateProperties.getCloud().getMixpanelToken();
    contextPath = context.getContextPath();
    canLogout = profileService.currentProfileCanLogout();
    isLoginDelegated = profileService.isLoginDelegated();
    tasklistUrl = operateProperties.getTasklistUrl();
    resourcePermissionsEnabled = operateProperties.getIdentity().isResourcePermissionsEnabled();
    try {
      return String.format("window.clientConfig = %s;",
          new ObjectMapper().writeValueAsString(this));
    } catch (JsonProcessingException e) {
      return "window.clientConfig = {};";
    }
  }
}
