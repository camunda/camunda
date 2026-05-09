/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller.system;

import static io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper.mapErrorToResponse;

import io.camunda.gateway.mapping.http.search.SearchQueryRequestMapper;
import io.camunda.gateway.mapping.http.search.SearchQueryResponseMapper;
import io.camunda.gateway.protocol.model.AuthenticationConfigurationResponse;
import io.camunda.gateway.protocol.model.CloudConfigurationResponse;
import io.camunda.gateway.protocol.model.ComponentsConfigurationResponse;
import io.camunda.gateway.protocol.model.DeploymentConfigurationResponse;
import io.camunda.gateway.protocol.model.JobMetricsConfigurationResponse;
import io.camunda.gateway.protocol.model.SystemConfigurationResponse;
import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.UsageMetricsServices;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import jakarta.servlet.ServletContext;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/system")
public class SystemController {

  private static final List<String> KNOWN_COMPONENTS = List.of("admin", "operate", "tasklist");

  private final UsageMetricsServices usageMetricsServices;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;
  private final SecurityConfiguration securityConfiguration;
  private final ServletContext servletContext;
  private final Environment environment;
  private final boolean enterprise;
  private final boolean loginDelegated;
  private final Long maxRequestSizeBytes;
  private final String cloudStage;
  private final String cloudMixpanelToken;
  private final String cloudMixpanelApiHost;

  public SystemController(
      final UsageMetricsServices usageMetricsServices,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration,
      @Autowired(required = false) final SecurityConfiguration securityConfiguration,
      @Autowired(required = false) final ServletContext servletContext,
      @Autowired(required = false) final Environment environment,
      @Value("${camunda.webapp.enterprise:false}") final boolean enterprise,
      @Value("${camunda.webapps.login-delegated:false}") final boolean loginDelegated,
      @Value("${spring.servlet.multipart.max-request-size:4MB}") final DataSize maxRequestSize,
      @Value("${camunda.webapp.cloud.stage:#{null}}") final String cloudStage,
      @Value("${camunda.webapp.cloud.mixpanel-token:#{null}}") final String cloudMixpanelToken,
      @Value("${camunda.webapp.cloud.mixpanel-api-host:#{null}}")
          final String cloudMixpanelApiHost) {
    this.usageMetricsServices = usageMetricsServices;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
    this.securityConfiguration = securityConfiguration;
    this.servletContext = servletContext;
    this.environment = environment;
    this.enterprise = enterprise;
    this.loginDelegated = loginDelegated;
    this.maxRequestSizeBytes = maxRequestSize != null ? maxRequestSize.toBytes() : null;
    this.cloudStage = cloudStage;
    this.cloudMixpanelToken = cloudMixpanelToken;
    this.cloudMixpanelApiHost = cloudMixpanelApiHost;
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/usage-metrics")
  public ResponseEntity<UsageMetricsResponse> getUsageMetrics(
      @RequestParam final String startTime,
      @RequestParam final String endTime,
      @RequestParam(required = false) final String tenantId,
      @RequestParam(required = false, defaultValue = "false") final boolean withTenants) {

    return SearchQueryRequestMapper.toUsageMetricsQuery(startTime, endTime, tenantId, withTenants)
        .fold(RestErrorMapper::mapProblemToResponse, this::getMetrics);
  }

  @CamundaGetMapping(path = "/configuration")
  public ResponseEntity<SystemConfigurationResponse> getSystemConfiguration() {
    final JobMetricsConfiguration jobMetricsCfg = gatewayRestConfiguration.getJobMetrics();
    final var jobMetricsResponse =
        JobMetricsConfigurationResponse.Builder.create()
            .enabled(jobMetricsCfg.isEnabled())
            .exportInterval(jobMetricsCfg.getExportInterval().toString())
            .maxWorkerNameLength(jobMetricsCfg.getMaxWorkerNameLength())
            .maxJobTypeLength(jobMetricsCfg.getMaxJobTypeLength())
            .maxTenantIdLength(jobMetricsCfg.getMaxTenantIdLength())
            .maxUniqueKeys(jobMetricsCfg.getMaxUniqueKeys())
            .build();

    final var responseBuilder =
        SystemConfigurationResponse.Builder.create().jobMetrics(jobMetricsResponse);

    // Add webapp configuration sections if dependencies are available
    if (environment != null) {
      responseBuilder
          .components(buildComponentsConfiguration())
          .deployment(buildDeploymentConfiguration())
          .authentication(buildAuthenticationConfiguration())
          .cloud(buildCloudConfiguration());
    }

    return ResponseEntity.ok(responseBuilder.build());
  }

  private ComponentsConfigurationResponse buildComponentsConfiguration() {
    final List<String> activeComponents =
        KNOWN_COMPONENTS.stream().filter(this::isComponentEnabled).toList();
    return ComponentsConfigurationResponse.Builder.create().active(activeComponents).build();
  }

  private boolean isComponentEnabled(final String name) {
    if (environment == null) {
      return false;
    }
    final boolean legacyEnabled =
        environment.getProperty("camunda." + name + ".webapp-enabled", Boolean.class, true);
    final boolean unifiedEnabled =
        environment.getProperty("camunda.webapps." + name + ".enabled", Boolean.class, true);
    final boolean uiEnabled =
        environment.getProperty("camunda.webapps." + name + ".ui-enabled", Boolean.class, true);
    return legacyEnabled && unifiedEnabled && uiEnabled;
  }

  private DeploymentConfigurationResponse buildDeploymentConfiguration() {
    final boolean isMultiTenancyEnabled =
        securityConfiguration != null && securityConfiguration.getMultiTenancy().isChecksEnabled();
    final String contextPath = servletContext != null ? servletContext.getContextPath() : "";

    return DeploymentConfigurationResponse.Builder.create()
        .isEnterprise(enterprise)
        .isMultiTenancyEnabled(isMultiTenancyEnabled)
        .contextPath(contextPath)
        .maxRequestSize(maxRequestSizeBytes)
        .build();
  }

  private AuthenticationConfigurationResponse buildAuthenticationConfiguration() {
    final boolean canLogout =
        securityConfiguration == null || securityConfiguration.getSaas().getClusterId() == null;

    return AuthenticationConfigurationResponse.Builder.create()
        .canLogout(canLogout)
        .isLoginDelegated(loginDelegated)
        .build();
  }

  private CloudConfigurationResponse buildCloudConfiguration() {
    final String organizationId =
        securityConfiguration != null ? securityConfiguration.getSaas().getOrganizationId() : null;
    final String clusterId =
        securityConfiguration != null ? securityConfiguration.getSaas().getClusterId() : null;

    return CloudConfigurationResponse.Builder.create()
        .organizationId(organizationId)
        .clusterId(clusterId)
        .stage(cloudStage)
        .mixpanelToken(cloudMixpanelToken)
        .mixpanelAPIHost(cloudMixpanelApiHost)
        .build();
  }

  private ResponseEntity<UsageMetricsResponse> getMetrics(final UsageMetricsQuery query) {
    try {
      return ResponseEntity.ok(
          SearchQueryResponseMapper.toUsageMetricsResponse(
              usageMetricsServices.search(query, authenticationProvider.getCamundaAuthentication()),
              query.filter().withTenants()));
    } catch (final Exception e) {
      return mapErrorToResponse(e);
    }
  }
}
