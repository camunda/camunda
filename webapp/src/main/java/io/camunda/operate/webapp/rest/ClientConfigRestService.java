/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.camunda.operate.webapp.rest;

import javax.servlet.ServletContext;
import io.camunda.operate.property.OperateProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientConfigRestService {

  public static final String CLIENT_CONFIG_RESOURCE = "/client-config.js";

  @Autowired
  private OperateProperties operateProperties;

  @Autowired
  private ServletContext context;

  private String indexPage;

  @GetMapping(path = CLIENT_CONFIG_RESOURCE, produces = "text/javascript")
  public String getClientConfig() {
    return String.format(
        "window.clientConfig = { \"isEnterprise\": %s, \"contextPath\": \"%s\" };",
        operateProperties.isEnterprise(),
        context.getContextPath()
    );
  }

}
