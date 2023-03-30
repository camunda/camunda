/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.webapp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import javax.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientConfigRestService {

  public static final String CLIENT_CONFIG_RESOURCE = "/client-config.js";

  @Autowired private ClientConfig clientConfig;

  private String clientConfigAsJS;

  @PostConstruct
  public void init() {
    try {
      clientConfigAsJS =
          String.format(
              "window.clientConfig = %s;", new ObjectMapper().writeValueAsString(clientConfig));
    } catch (JsonProcessingException e) {
      clientConfigAsJS = "window.clientConfig = {};";
    }
  }

  @Hidden
  @GetMapping(path = CLIENT_CONFIG_RESOURCE, produces = "text/javascript")
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
