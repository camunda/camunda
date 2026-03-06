/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication;

import io.camunda.application.commons.condition.ConditionalOnAnyHttpGatewayEnabled;
import io.camunda.authentication.ConditionalOnAuthenticationMethod;
import io.camunda.authentication.ConditionalOnUnprotectedApi;
import io.camunda.authentication.DefaultCamundaAuthenticationProvider;
import io.camunda.authentication.converter.CamundaAuthenticationDelegatingConverter;
import io.camunda.authentication.converter.UnprotectedCamundaAuthenticationConverter;
import io.camunda.authentication.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.authentication.holder.HttpSessionBasedAuthenticationHolder;
import io.camunda.authentication.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.authentication.service.MembershipService;
import io.camunda.authentication.service.NoDBMembershipService;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.security.entity.AuthenticationMethod;
import io.camunda.security.reader.ResourceAccessProvider;
import io.camunda.service.GroupServices;
import io.camunda.service.MappingRuleServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import io.camunda.service.UserServices;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageDisabled;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import io.camunda.zeebe.gateway.rest.controller.authentication.CamundaUserService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

@Configuration(proxyBeanMethods = false)
@ConditionalOnAnyHttpGatewayEnabled
public class CamundaAuthenticationConfiguration {

  @Bean
  @ConditionalOnUnprotectedApi
  public CamundaAuthenticationConverter<Authentication> unprotectedAuthenticationConverter() {
    return new UnprotectedCamundaAuthenticationConverter();
  }

  @Bean
  public CamundaAuthenticationHolder requestContextBasedAuthenticationHolder(
      final HttpServletRequest request) {
    return new RequestContextBasedAuthenticationHolder(request);
  }

  @Bean
  public CamundaAuthenticationHolder httpSessionBasedAuthenticationHolder(
      final HttpServletRequest request, final SecurityConfiguration securityConfiguration) {
    return new HttpSessionBasedAuthenticationHolder(
        request, securityConfiguration.getAuthentication());
  }

  @Bean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final List<CamundaAuthenticationHolder> holders,
      final List<CamundaAuthenticationConverter<Authentication>> converters) {
    return new DefaultCamundaAuthenticationProvider(
        new CamundaAuthenticationDelegatingHolder(holders),
        new CamundaAuthenticationDelegatingConverter(converters));
  }

  @Bean
  @ConditionalOnSecondaryStorageEnabled
  public WebComponentAuthorizationCheckFilter webComponentAuthorizationCheckFilter(
      final SecurityConfiguration securityConfig,
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider) {
    return new WebComponentAuthorizationCheckFilter(
        securityConfig, authenticationProvider, resourceAccessProvider);
  }

  @Bean
  @Primary
  @ConditionalOnSecondaryStorageEnabled
  public MembershipService membershipService(
      final MappingRuleServices mappingRuleServices,
      final TenantServices tenantServices,
      final RoleServices roleServices,
      final GroupServices groupServices,
      final SecurityConfiguration securityConfiguration) {
    return new DefaultMembershipService(
        mappingRuleServices, tenantServices, roleServices, groupServices, securityConfiguration);
  }

  @Bean
  @ConditionalOnSecondaryStorageDisabled
  public MembershipService noDBMembershipService(SecurityConfiguration securityConfiguration) {
    return new NoDBMembershipService(securityConfiguration);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @ConditionalOnSecondaryStorageEnabled
  @Profile("consolidated-auth")
  public CamundaUserService oidcCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final TenantServices tenantServices,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final HttpServletRequest request) {
    return new OidcCamundaUserService(
        authenticationProvider,
        resourceAccessProvider,
        tenantServices,
        authorizedClientRepository,
        request);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageEnabled
  @Profile("consolidated-auth")
  public CamundaUserService basicCamundaUserService(
      final CamundaAuthenticationProvider authenticationProvider,
      final ResourceAccessProvider resourceAccessProvider,
      final UserServices userServices,
      final TenantServices tenantServices) {
    return new BasicCamundaUserService(
        authenticationProvider, resourceAccessProvider, userServices, tenantServices);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageEnabled
  public CamundaAuthenticationConverter<Authentication> usernamePasswordAuthenticationConverter(
      final RoleServices roleServices,
      final GroupServices groupServices,
      final TenantServices tenantServices) {
    return new UsernamePasswordAuthenticationTokenConverter(
        roleServices, groupServices, tenantServices);
  }

  @Bean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnSecondaryStorageEnabled
  public AdminUserCheckFilter adminUserCheckFilter(
      final SecurityConfiguration securityConfig, final RoleServices roleServices) {
    return new AdminUserCheckFilter(securityConfig, roleServices);
  }
}
