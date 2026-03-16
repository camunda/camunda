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
import io.camunda.gatekeeper.config.AuthenticationConfig;
import io.camunda.gatekeeper.model.identity.AuthenticationMethod;
import io.camunda.security.configuration.SecurityConfiguration;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AdminClientConfigController {
  private static final Logger LOG = LoggerFactory.getLogger(AdminClientConfigController.class);

  private static final String IS_OIDC = "isOidc";
  private static final String IS_CAMUNDA_GROUPS_ENABLED = "isCamundaGroupsEnabled";
  private static final String IS_TENANTS_API_ENABLED = "isTenantsApiEnabled";
  private static final String ORGANIZATION_ID = "organizationId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String ID_PATTERN = "idPattern";
  private static final String FALLBACK_CONFIG_JS = "window.clientConfig = {};";
  private static final String CONFIG_JS_TEMPLATE = "window.clientConfig = %s;";

  private final String clientConfigAsJS;
  private final ObjectMapper objectMapper;

  public AdminClientConfigController(
      final AuthenticationConfig authenticationConfig,
      final SecurityConfiguration securityConfiguration) {
    objectMapper = new ObjectMapper();
    clientConfigAsJS = generateClientConfig(authenticationConfig, securityConfiguration);
  }

  private String generateClientConfig(
      final AuthenticationConfig authenticationConfig,
      final SecurityConfiguration securityConfiguration) {
    try {
      final Map<String, String> config =
          createConfigMap(authenticationConfig, securityConfiguration);
      final String configJson = objectMapper.writeValueAsString(config);
      return String.format(CONFIG_JS_TEMPLATE, configJson);
    } catch (final JsonProcessingException e) {
      LOG.warn("Error serializing client config to JSON", e);
      return FALLBACK_CONFIG_JS;
    }
  }

  private Map<String, String> createConfigMap(
      final AuthenticationConfig authenticationConfig,
      final SecurityConfiguration securityConfiguration) {
    final var config = new java.util.HashMap<String, String>();
    final var saasConfiguration = securityConfiguration.getSaas();

    config.put(IS_OIDC, String.valueOf(isOidcAuthentication(authenticationConfig)));
    config.put(
        IS_CAMUNDA_GROUPS_ENABLED, String.valueOf(isCamundaGroupsEnabled(authenticationConfig)));
    config.put(
        IS_TENANTS_API_ENABLED,
        String.valueOf(securityConfiguration.getMultiTenancy().isApiEnabled()));
    config.put(ORGANIZATION_ID, saasConfiguration.getOrganizationId());
    config.put(CLUSTER_ID, saasConfiguration.getClusterId());
    config.put(ID_PATTERN, securityConfiguration.getIdValidationPattern());

    return config;
  }

  private boolean isOidcAuthentication(final AuthenticationConfig authenticationConfig) {
    return AuthenticationMethod.OIDC.equals(authenticationConfig.method());
  }

  private boolean isCamundaGroupsEnabled(final AuthenticationConfig authenticationConfig) {
    final var oidcConfig = authenticationConfig.oidc();
    return oidcConfig == null || !oidcConfig.isGroupsClaimConfigured();
  }

  @GetMapping(path = "/admin/config.js", produces = "text/javascript;charset=UTF-8")
  @ResponseBody
  @Hidden
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
