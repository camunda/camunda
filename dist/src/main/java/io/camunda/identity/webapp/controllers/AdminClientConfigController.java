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
import io.camunda.security.api.model.authz.AuthorizationResourceType;
import io.camunda.security.api.model.authz.DefaultRole;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.configuration.SaasConfigurationHelper;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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
  private static final String RESOURCE_PERMISSIONS = "resourcePermissions";
  private static final String DEFAULT_ROLE_IDS = "defaultRoleIds";
  private static final String IS_NEW_DESIGN_SYSTEM_ENABLED = "isNewDesignSystemEnabled";
  private static final String FALLBACK_CONFIG_JS = "window.clientConfig = {};";
  private static final String CONFIG_JS_TEMPLATE = "window.clientConfig = %s;";

  private final String clientConfigAsJS;
  private final ObjectMapper objectMapper;

  public AdminClientConfigController(
      final CamundaSecurityLibraryProperties cslProperties,
      @Autowired(required = false) final WebappConfiguration webappConfiguration) {
    final var resolvedWebappConfiguration =
        webappConfiguration != null ? webappConfiguration : new WebappConfiguration();
    final boolean isSaas = SaasConfigurationHelper.isSaas(cslProperties.getSaas());
    final boolean isNewDesignSystemEnabled =
        resolvedWebappConfiguration.resolveNewDesignSystemEnabled(isSaas);

    LOG.info(
        "Admin new design system resolved to {} (explicit override={}, isSaas={})",
        isNewDesignSystemEnabled,
        resolvedWebappConfiguration.getNewDesignSystemEnabled(),
        isSaas);

    objectMapper = new ObjectMapper();
    clientConfigAsJS = generateClientConfig(cslProperties, isNewDesignSystemEnabled);
  }

  private String generateClientConfig(
      final CamundaSecurityLibraryProperties cslProperties,
      final boolean isNewDesignSystemEnabled) {
    try {
      final Map<String, Object> config = createConfigMap(cslProperties, isNewDesignSystemEnabled);
      final String configJson = objectMapper.writeValueAsString(config);
      return String.format(CONFIG_JS_TEMPLATE, configJson);
    } catch (final JsonProcessingException e) {
      LOG.warn("Error serializing client config to JSON", e);
      return FALLBACK_CONFIG_JS;
    }
  }

  private Map<String, Object> createConfigMap(
      final CamundaSecurityLibraryProperties cslProperties,
      final boolean isNewDesignSystemEnabled) {
    final var config = new java.util.HashMap<String, Object>();
    final var saasConfiguration = cslProperties.getSaas();

    config.put(IS_OIDC, String.valueOf(isOidcAuthentication(cslProperties)));
    config.put(IS_CAMUNDA_GROUPS_ENABLED, String.valueOf(isCamundaGroupsEnabled(cslProperties)));
    config.put(
        IS_TENANTS_API_ENABLED, String.valueOf(cslProperties.getMultiTenancy().isApiEnabled()));
    config.put(ORGANIZATION_ID, saasConfiguration.getOrganizationId());
    config.put(CLUSTER_ID, saasConfiguration.getClusterId());
    config.put(ID_PATTERN, cslProperties.getIdValidationPattern());
    config.put(RESOURCE_PERMISSIONS, AuthorizationResourceType.buildResourcePermissionsMap());
    config.put(DEFAULT_ROLE_IDS, DefaultRole.ids());
    config.put(IS_NEW_DESIGN_SYSTEM_ENABLED, String.valueOf(isNewDesignSystemEnabled));

    return config;
  }

  private boolean isOidcAuthentication(final CamundaSecurityLibraryProperties cslProperties) {
    return AuthenticationMethod.OIDC.equals(cslProperties.getAuthentication().getMethod());
  }

  private boolean isCamundaGroupsEnabled(final CamundaSecurityLibraryProperties cslProperties) {
    final var authentication = cslProperties.getAuthentication();
    final var oidcConfig = authentication.getOidc();
    return oidcConfig == null
        || oidcConfig.getGroupsClaim() == null
        || oidcConfig.getGroupsClaim().isEmpty();
  }

  @GetMapping(path = "/admin/config.js", produces = "text/javascript;charset=UTF-8")
  @ResponseBody
  @Hidden
  public String getClientConfig() {
    return clientConfigAsJS;
  }
}
