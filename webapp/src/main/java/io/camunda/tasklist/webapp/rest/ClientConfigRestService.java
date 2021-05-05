/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.tasklist.webapp.rest;

import io.camunda.tasklist.property.TasklistProperties;
import javax.servlet.ServletContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientConfigRestService {

  public static final String CLIENT_CONFIG_RESOURCE = "/client-config.js";

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private ServletContext context;

  @GetMapping(path = CLIENT_CONFIG_RESOURCE, produces = "text/javascript")
  public String getClientConfig() {
    return String.format(
        "window.clientConfig = { \"isEnterprise\": %s, \"contextPath\": \"%s\" };",
        tasklistProperties.isEnterprise(), context.getContextPath());
  }
}
