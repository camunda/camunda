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
public class IdentityClientConfigController {

  private static final Logger LOG = LoggerFactory.getLogger(IdentityClientConfigController.class);

  private static final String IS_OIDC = "isOidc";
  private static final String IS_CAMUNDA_GROUPS_ENABLED = "isCamundaGroupsEnabled";
  private static final String IS_TENANTS_API_ENABLED = "isTenantsApiEnabled";
  private static final String ORGANIZATION_ID = "organizationId";
  private static final String CLUSTER_ID = "clusterId";
  private static final String FALLBACK_CONFIG_JS = "window.clientConfig = {};";
  private static final String CONFIG_JS_TEMPLATE = "window.clientConfig = %s;";

  private final String clientConfigAsJS;
  private final ObjectMapper objectMapper;

  public IdentityClientConfigController(final SecurityConfiguration securityConfiguration) {
    objectMapper = new ObjectMapper();
    clientConfigAsJS = generateClientConfig(securityConfiguration);
  }

  private String generateClientConfig(final SecurityConfiguration securityConfiguration) {
    try {
      final Map<String, String> config = createConfigMap(securityConfiguration);
      final String configJson = objectMapper.writeValueAsString(config);
      return String.format(CONFIG_JS_TEMPLATE, configJson);
    } catch (final JsonProcessingException e) {
      LOG.warn("Error serializing client config to JSON", e);
      return FALLBACK_CONFIG_JS;
    }
  }

  private Map<String, String> createConfigMap(final SecurityConfiguration securityConfiguration) {
    final var config = new java.util.HashMap<String, String>();
    final var saasConfiguration = securityConfiguration.getSaas();

    config.put(IS_OIDC, String.valueOf(isOidcAuthentication(securityConfiguration)));
    config.put(
        IS_CAMUNDA_GROUPS_ENABLED, String.valueOf(isCamundaGroupsEnabled(securityConfiguration)));
    config.put(
        IS_TENANTS_API_ENABLED,
        String.valueOf(securityConfiguration.getMultiTenancy().isApiEnabled()));
    config.put(ORGANIZATION_ID, saasConfiguration.getOrganizationId());
    config.put(CLUSTER_ID, saasConfiguration.getClusterId());

    return config;
  }

  private boolean isOidcAuthentication(final SecurityConfiguration securityConfiguration) {
    return AuthenticationMethod.OIDC.equals(securityConfiguration.getAuthentication().getMethod());
  }

  private boolean isCamundaGroupsEnabled(final SecurityConfiguration securityConfiguration) {
    final var authentication = securityConfiguration.getAuthentication();
    final var oidcConfig = authentication.getOidc().values().stream().findFirst().get();
    return oidcConfig == null
        || oidcConfig.getGroupsClaim() == null
        || oidcConfig.getGroupsClaim().isEmpty();
  }

  @GetMapping(path = "/identity/config.js", produces = "text/javascript;charset=UTF-8")
  @ResponseBody
  @Hidden
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
