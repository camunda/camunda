/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.identity.webapp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class IdentityClientConfigRestController {

  private static final Logger LOG =
      LoggerFactory.getLogger(IdentityClientConfigRestController.class);

  private static final String VITE_IS_OIDC = "VITE_IS_OIDC";
  private static final String VITE_INTERNAL_GROUPS_ENABLED = "VITE_INTERNAL_GROUPS_ENABLED";

  private String clientConfigAsJS;

  public IdentityClientConfigRestController(final SecurityConfiguration securityConfiguration) {
    try {
      clientConfigAsJS =
          String.format(
              "window.clientConfig = %s;",
              new ObjectMapper()
                  .writeValueAsString(
                      Map.of(
                          VITE_IS_OIDC,
                          String.valueOf(
                              AuthenticationMethod.OIDC.equals(
                                  securityConfiguration.getAuthentication().getMethod())),
                          VITE_INTERNAL_GROUPS_ENABLED,
                          String.valueOf(
                              securityConfiguration.getAuthentication().getOidc() == null
                                  || securityConfiguration
                                          .getAuthentication()
                                          .getOidc()
                                          .getGroupsClaim()
                                      == null))));
    } catch (final JsonProcessingException e) {
      LOG.warn("Error when serializing client config", e);
      clientConfigAsJS = "window.clientConfig = {};";
    }
  }

  @GetMapping(path = "/identity/config.js", produces = "text/javascript")
  @ResponseBody
  @Hidden
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
