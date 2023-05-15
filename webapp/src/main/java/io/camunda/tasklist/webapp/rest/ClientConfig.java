/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.rest;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistProfileService;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ClientConfig {

  public boolean isEnterprise;
  public boolean canLogout;
  public boolean isLoginDelegated;
  public String contextPath;
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

  @Autowired private TasklistProfileService profileService;
  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private ServletContext context;

  @PostConstruct
  public void init() {
    isEnterprise = tasklistProperties.isEnterprise();
    contextPath = context.getContextPath();
    canLogout = profileService.currentProfileCanLogout();
    isLoginDelegated = profileService.isLoginDelegated();
  }
}
