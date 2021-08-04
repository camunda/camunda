/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.rest;

import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.webapp.security.TasklistURIs;
import java.util.Arrays;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientConfigRestService {

  public static final String CLIENT_CONFIG_RESOURCE = "/client-config.js";

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ServletContext context;

  @Autowired private Environment environment;

  @GetMapping(path = CLIENT_CONFIG_RESOURCE, produces = "text/javascript")
  public String getClientConfig() {
    final boolean canLogout =
        !Arrays.asList(environment.getActiveProfiles()).contains(TasklistURIs.SSO_AUTH_PROFILE);
    return String.format(
        "window.clientConfig = { \"isEnterprise\": %s, \"contextPath\": \"%s\", \"canLogout\": %b };",
        tasklistProperties.isEnterprise(), context.getContextPath(), canLogout);
  }
}
