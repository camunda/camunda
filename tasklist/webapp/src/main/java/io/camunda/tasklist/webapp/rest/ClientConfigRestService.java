/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.webapp.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ClientConfigRestService {

  public static final String CLIENT_CONFIG_RESOURCE = "/tasklist/client-config.js";

  @Autowired private ClientConfig clientConfig;

  private String clientConfigAsJS;

  @PostConstruct
  public void init() {
    try {
      clientConfigAsJS =
          String.format(
              "window.clientConfig = %s;", new ObjectMapper().writeValueAsString(clientConfig));
    } catch (final JsonProcessingException e) {
      clientConfigAsJS = "window.clientConfig = {};";
    }
  }

  @Hidden
  @GetMapping(path = CLIENT_CONFIG_RESOURCE, produces = "text/javascript")
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
