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
import io.camunda.gateway.protocol.model.CloudStage;
import io.camunda.gateway.protocol.model.ComponentsConfigurationResponse;
import io.camunda.gateway.protocol.model.DeploymentConfigurationResponse;
import io.camunda.gateway.protocol.model.JobMetricsConfigurationResponse;
import io.camunda.gateway.protocol.model.SystemConfigurationResponse;
import io.camunda.gateway.protocol.model.UsageMetricsResponse;
import io.camunda.gateway.protocol.model.WebappComponent;
import io.camunda.search.query.UsageMetricsQuery;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SaasConfigurationHelper;
import io.camunda.security.spring.CamundaSecurityLibraryProperties;
import io.camunda.service.UsageMetricsServices;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.annotation.CamundaGetMapping;
import io.camunda.zeebe.gateway.rest.annotation.PhysicalTenantId;
import io.camunda.zeebe.gateway.rest.annotation.RequiresSecondaryStorage;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.JobMetricsConfiguration;
import io.camunda.zeebe.gateway.rest.config.WebappConfiguration;
import io.camunda.zeebe.gateway.rest.controller.CamundaRestController;
import io.camunda.zeebe.gateway.rest.mapper.RestErrorMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.util.unit.DataSize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@CamundaRestController
@RequestMapping("/v2/system")
public class SystemController {

  private final ServiceRegistry registry;
  private final CamundaAuthenticationProvider authenticationProvider;
  private final GatewayRestConfiguration gatewayRestConfiguration;
  private final CamundaSecurityLibraryProperties cslProperties;
  private final WebappConfiguration webappConfiguration;
  private final long maxRequestSizeBytes;

  public SystemController(
      final ServiceRegistry registry,
      final CamundaAuthenticationProvider authenticationProvider,
      final GatewayRestConfiguration gatewayRestConfiguration,
      @Autowired(required = false) final CamundaSecurityLibraryProperties cslProperties,
      @Autowired(required = false) final WebappConfiguration webappConfiguration,
      @Value("${spring.servlet.multipart.max-request-size:4MB}") final DataSize maxRequestSize) {
    this.registry = registry;
    this.authenticationProvider = authenticationProvider;
    this.gatewayRestConfiguration = gatewayRestConfiguration;
    this.cslProperties = cslProperties;
    this.webappConfiguration =
        webappConfiguration != null ? webappConfiguration : new WebappConfiguration();
    this.maxRequestSizeBytes = maxRequestSize.toBytes();
  }

  @RequiresSecondaryStorage
  @CamundaGetMapping(path = "/usage-metrics")
  public ResponseEntity<UsageMetricsResponse> getUsageMetrics(
      @PhysicalTenantId final String physicalTenantId,
      @RequestParam final String startTime,
      @RequestParam final String endTime,
      @RequestParam(required = false) final String tenantId,
      @RequestParam(required = false, defaultValue = "false") final boolean withTenants) {

    return SearchQueryRequestMapper.toUsageMetricsQuery(startTime, endTime, tenantId, withTenants)
        .fold(
            RestErrorMapper::mapProblemToResponse,
            query -> getMetrics(registry.usageMetricsServices(physicalTenantId), query));
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

    return ResponseEntity.ok(
        SystemConfigurationResponse.Builder.create()
            .jobMetrics(jobMetricsResponse)
            .components(buildComponentsConfiguration())
            .deployment(buildDeploymentConfiguration())
            .authentication(buildAuthenticationConfiguration())
            .cloud(buildCloudConfiguration())
            .build());
  }

  private ComponentsConfigurationResponse buildComponentsConfiguration() {
    return ComponentsConfigurationResponse.Builder.create()
        .active(
            webappConfiguration.getActiveComponents().stream()
                .map(WebappComponent::fromValue)
                .toList())
        .build();
  }

  private DeploymentConfigurationResponse buildDeploymentConfiguration() {
    final boolean isMultiTenancyEnabled =
        cslProperties != null
            && cslProperties.getMultiTenancy() != null
            && cslProperties.getMultiTenancy().isChecksEnabled();

    return DeploymentConfigurationResponse.Builder.create()
        .isMultiTenancyEnabled(isMultiTenancyEnabled)
        .maxRequestSize(maxRequestSizeBytes)
        .build();
  }

  private AuthenticationConfigurationResponse buildAuthenticationConfiguration() {
    final boolean canLogout =
        !SaasConfigurationHelper.isSaas(cslProperties != null ? cslProperties.getSaas() : null);

    return AuthenticationConfigurationResponse.Builder.create()
        .canLogout(canLogout)
        .isLoginDelegated(webappConfiguration.isLoginDelegated())
        .build();
  }

  private CloudConfigurationResponse buildCloudConfiguration() {
    return CloudConfigurationResponse.Builder.create()
        .stage(
            webappConfiguration.getCloud().getStage() != null
                ? CloudStage.fromValue(webappConfiguration.getCloud().getStage())
                : null)
        .build();
  }

  private ResponseEntity<UsageMetricsResponse> getMetrics(
      final UsageMetricsServices usageMetricsServices, final UsageMetricsQuery query) {
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
