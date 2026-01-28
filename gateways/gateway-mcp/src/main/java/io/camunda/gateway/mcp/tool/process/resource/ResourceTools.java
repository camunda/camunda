/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gateway.mcp.tool.process.resource;

import io.camunda.gateway.mapping.http.ResponseMapper;
import io.camunda.gateway.mapping.http.SimpleRequestMapper;
import io.camunda.gateway.mcp.mapper.CallToolResultMapper;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ResourceServices;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
public class ResourceTools {

  private final ResourceServices resourceServices;
  private final MultiTenancyConfiguration multiTenancyCfg;
  private final CamundaAuthenticationProvider authenticationProvider;

  public ResourceTools(
      final ResourceServices resourceServices,
      final MultiTenancyConfiguration multiTenancyCfg,
      final CamundaAuthenticationProvider authenticationProvider) {
    this.resourceServices = resourceServices;
    this.multiTenancyCfg = multiTenancyCfg;
    this.authenticationProvider = authenticationProvider;
  }

  @McpTool(
      description =
          "Deploy resources (BPMN processes, DMN decisions, Forms, or RPA bots) to Camunda. Resources must be provided as a map of resource names to base64-encoded content.")
  public CallToolResult deployResources(
      @McpToolParam(
              description =
                  "Map of resource names to base64-encoded content. Resource names should include appropriate file extensions (.bpmn, .dmn, .form, .rpa). Example: {\"process.bpmn\": \"PD94bWw...\", \"decision.dmn\": \"PD94bWw...\"}")
          @NotEmpty(message = "Resources must not be empty.")
          final Map<@NotBlank String, @NotBlank String> resources,
      @McpToolParam(
              description =
                  "The tenant ID for multi-tenancy. If not specified, the default tenant is used.",
              required = false)
          final String tenantId) {
    try {
      final var deployRequest =
          SimpleRequestMapper.toDeployResourceRequest(
              resources, tenantId, multiTenancyCfg.isChecksEnabled());
      if (deployRequest.isLeft()) {
        return CallToolResultMapper.mapProblemToResult(deployRequest.getLeft());
      }

      return CallToolResultMapper.from(
          resourceServices
              .withAuthentication(authenticationProvider.getCamundaAuthentication())
              .deployResources(deployRequest.get()),
          ResponseMapper::toDeployResourceResponse);
    } catch (final Exception e) {
      return CallToolResultMapper.mapErrorToResult(e);
    }
  }
}
