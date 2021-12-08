/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import io.camunda.operate.property.OperateProperties;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ClientConfig {

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ServletContext context;

  public boolean isEnterprise;

  public String contextPath;

  public String organizationId;

  public String clusterId;

  /**
   * Temporary feature flag
   */
  public boolean mixpanelActivated;

  @PostConstruct
  public void init(){
    isEnterprise = operateProperties.isEnterprise();
    clusterId = operateProperties.getCloud().getClusterId();
    organizationId = operateProperties.getCloud().getOrganizationId();
    contextPath = context.getContextPath();
    mixpanelActivated = false;
  }
}
