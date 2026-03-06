/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.port.inbound.AuthenticationPort;
import io.camunda.auth.domain.spi.CamundaAuthenticationConverter;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.auth.domain.spi.MembershipResolver;
import io.camunda.auth.spring.DefaultCamundaAuthenticationProvider;
import io.camunda.auth.spring.SpringAuthenticationAdapter;
import io.camunda.auth.spring.SpringOidcTokenExchangeConverter;
import io.camunda.auth.spring.converter.DelegatingAuthenticationConverter;
import io.camunda.auth.spring.converter.NoOpMembershipResolver;
import io.camunda.auth.spring.converter.OidcTokenAuthenticationConverter;
import io.camunda.auth.spring.converter.TokenClaimsConverter;
import io.camunda.auth.spring.holder.CamundaAuthenticationDelegatingHolder;
import io.camunda.auth.spring.holder.CamundaAuthenticationHolder;
import io.camunda.auth.spring.holder.RequestContextBasedAuthenticationHolder;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

/**
 * Core auto-configuration for the Camunda Auth SDK. Wires up the authentication provider, converter
 * chain, and membership resolver.
 */
@AutoConfiguration
@EnableConfigurationProperties(CamundaAuthProperties.class)
public class CamundaAuthAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AuthenticationPort authenticationPort() {
    return new SpringAuthenticationAdapter();
  }

  @Bean
  @ConditionalOnMissingBean
  public SpringOidcTokenExchangeConverter springOidcTokenExchangeConverter() {
    return new SpringOidcTokenExchangeConverter();
  }

  @Bean
  @ConditionalOnMissingBean
  public MembershipResolver membershipResolver(final CamundaAuthProperties properties) {
    final String groupsClaim = properties.getOidc().getGroupsClaim();
    return new NoOpMembershipResolver(
        groupsClaim != null && !groupsClaim.isBlank() ? groupsClaim : null);
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenClaimsConverter tokenClaimsConverter(
      final CamundaAuthProperties properties, final MembershipResolver membershipResolver) {
    final var oidcProps = properties.getOidc();
    return new TokenClaimsConverter(
        oidcProps.getUsernameClaim(),
        oidcProps.getClientIdClaim(),
        oidcProps.isPreferUsernameClaim(),
        membershipResolver);
  }

  @Bean
  @ConditionalOnMissingBean
  public OidcTokenAuthenticationConverter oidcTokenAuthenticationConverter(
      final TokenClaimsConverter tokenClaimsConverter) {
    return new OidcTokenAuthenticationConverter(tokenClaimsConverter);
  }

  @Bean
  @ConditionalOnMissingBean(CamundaAuthenticationConverter.class)
  public DelegatingAuthenticationConverter delegatingAuthenticationConverter(
      final OidcTokenAuthenticationConverter oidcConverter) {
    return new DelegatingAuthenticationConverter(
        List.of((CamundaAuthenticationConverter<Authentication>) oidcConverter));
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaAuthenticationHolder camundaAuthenticationHolder() {
    return new CamundaAuthenticationDelegatingHolder(
        List.of(new RequestContextBasedAuthenticationHolder()));
  }

  @Bean
  @ConditionalOnMissingBean
  public CamundaAuthenticationProvider camundaAuthenticationProvider(
      final CamundaAuthenticationHolder holder,
      final DelegatingAuthenticationConverter converter) {
    return new DefaultCamundaAuthenticationProvider(holder, converter);
  }
}
