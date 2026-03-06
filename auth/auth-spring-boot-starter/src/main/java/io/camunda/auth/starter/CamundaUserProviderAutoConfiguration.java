/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.model.AuthenticationMethod;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.CamundaUserProvider;
import io.camunda.auth.domain.spi.TenantInfoProvider;
import io.camunda.auth.domain.spi.UserProfileProvider;
import io.camunda.auth.domain.spi.WebComponentAccessProvider;
import io.camunda.auth.spring.user.BasicCamundaUserProvider;
import io.camunda.auth.spring.user.OidcCamundaUserProvider;
import io.camunda.auth.starter.condition.ConditionalOnAuthenticationMethod;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;

/**
 * Auto-configuration for CamundaUserProvider. Registers either BasicCamundaUserProvider or
 * OidcCamundaUserProvider based on the configured authentication method.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.method")
public class CamundaUserProviderAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.BASIC)
  @ConditionalOnBean({UserProfileProvider.class, TenantInfoProvider.class})
  public CamundaUserProvider basicCamundaUserProvider(
      final CamundaAuthenticationProvider authenticationProvider,
      final WebComponentAccessProvider componentAccessProvider,
      final UserProfileProvider userProfileProvider,
      final TenantInfoProvider tenantInfoProvider) {
    return new BasicCamundaUserProvider(
        authenticationProvider, componentAccessProvider, userProfileProvider, tenantInfoProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
  @ConditionalOnBean(TenantInfoProvider.class)
  public CamundaUserProvider oidcCamundaUserProvider(
      final CamundaAuthenticationProvider authenticationProvider,
      final WebComponentAccessProvider componentAccessProvider,
      final TenantInfoProvider tenantInfoProvider,
      final OAuth2AuthorizedClientRepository authorizedClientRepository,
      final HttpServletRequest request) {
    return new OidcCamundaUserProvider(
        authenticationProvider,
        componentAccessProvider,
        tenantInfoProvider,
        authorizedClientRepository,
        request);
  }
}
