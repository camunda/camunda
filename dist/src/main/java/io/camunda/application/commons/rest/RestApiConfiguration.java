/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.rest;

import io.camunda.application.commons.rest.RestApiConfiguration.GatewayRestProperties;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.authentication.HttpSessionCamundaAuthenticationCache;
import io.camunda.authentication.converter.BasicAuthenticationConverter;
import io.camunda.authentication.converter.DeferredMembershipResolver;
import io.camunda.authentication.converter.OAuth2TokenAuthenticationConverter;
import io.camunda.authentication.converter.OAuth2UserAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationCache;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.auth.CompositeCamundaAuthenticationConverter;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.zeebe.broker.client.api.BrokerTopologyManager;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration;
import io.camunda.zeebe.gateway.rest.util.ProcessElementProvider;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.Authentication;

@Configuration(proxyBeanMethods = false)
@ComponentScan(basePackages = "io.camunda.zeebe.gateway.rest")
@ConditionalOnRestGatewayEnabled
@EnableConfigurationProperties(GatewayRestProperties.class)
public class RestApiConfiguration {

  @Bean
  public ProcessElementProvider processElementProvider(
      final ProcessDefinitionServices processDefinitionServices) {
    return new ProcessElementProvider(processDefinitionServices);
  }

  @Bean
  public ProcessCache processCache(
      final GatewayRestConfiguration configuration,
      final ProcessElementProvider processElementProvider,
      final BrokerTopologyManager brokerTopologyManager,
      final MeterRegistry meterRegistry) {

    return new ProcessCache(
        configuration, processElementProvider, brokerTopologyManager, meterRegistry);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> basicAuthenticationConverter(
      final UserServices userServices) {
    return new BasicAuthenticationConverter(userServices);
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> oauth2UserAuthenticationConverter(
      final SecurityConfiguration configuration) {
    return new OAuth2UserAuthenticationConverter(
        configuration.getAuthentication().getOidc().getUsernameClaim(),
        configuration.getAuthentication().getOidc().getGroupsClaim());
  }

  @Bean
  public CamundaAuthenticationConverter<Authentication> oauth2TokenAuthenticationConverter(
      final SecurityConfiguration configuration) {
    return new OAuth2TokenAuthenticationConverter(
        configuration.getAuthentication().getOidc().getUsernameClaim(),
        configuration.getAuthentication().getOidc().getClientIdClaim(),
        configuration.getAuthentication().getOidc().getGroupsClaim());
  }

  @Bean
  public CamundaAuthenticationCache camundaAuthenticationCache(final HttpServletRequest request) {
    return new HttpSessionCamundaAuthenticationCache(request);
  }

  @Bean
  public DeferredMembershipResolver membershipResolver(
      final MappingServices mappingServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices) {
    return new DeferredMembershipResolver(
        mappingServices, tenantServices, roleServices, groupServices);
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationConverter<Authentication>> converters,
      final CamundaAuthenticationCache holder,
      final DeferredMembershipResolver resolver) {
    return new DefaultCamundaAuthenticationProvider(
        new CompositeCamundaAuthenticationConverter<>(converters), holder, resolver);
  }

  @ConfigurationProperties("camunda.rest")
  public static final class GatewayRestProperties extends GatewayRestConfiguration {}
}
