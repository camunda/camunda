/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.starter;

import io.camunda.auth.domain.port.inbound.TokenExchangePort;
import io.camunda.auth.domain.port.outbound.TokenCachePort;
import io.camunda.auth.domain.port.outbound.TokenExchangeClient;
import io.camunda.auth.domain.port.outbound.TokenStorePort;
import io.camunda.auth.domain.service.DelegationChainValidator;
import io.camunda.auth.domain.service.TokenExchangeService;
import io.camunda.auth.exchange.cache.InMemoryTokenCache;
import io.camunda.auth.spring.SpringSecurityTokenExchangeClient;
import io.camunda.auth.starter.config.CamundaAuthProperties;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;

/**
 * Auto-configuration for token exchange, enabled when {@code
 * camunda.auth.token-exchange.enabled=true}.
 *
 * <p>This configuration leverages Spring Security's built-in {@link OAuth2AuthorizedClientManager}
 * for RFC 8693 token exchange. The client registration is configured via standard Spring Security
 * OAuth2 Client properties.
 */
@AutoConfiguration(after = CamundaAuthAutoConfiguration.class)
@ConditionalOnProperty(name = "camunda.auth.token-exchange.enabled", havingValue = "true")
public class CamundaTokenExchangeAutoConfiguration {

  /**
   * Creates a {@link TokenExchangeClient} backed by Spring Security's {@link
   * OAuth2AuthorizedClientManager}. This delegates to Spring Security's built-in support for OAuth2
   * Token Exchange (RFC 8693), JWT Bearer, and other grant types.
   */
  @Bean
  @ConditionalOnMissingBean(TokenExchangeClient.class)
  @ConditionalOnBean(OAuth2AuthorizedClientManager.class)
  public SpringSecurityTokenExchangeClient springSecurityTokenExchangeClient(
      final OAuth2AuthorizedClientManager authorizedClientManager,
      final CamundaAuthProperties properties) {
    final String registrationId = properties.getTokenExchange().getClientRegistrationId();
    return new SpringSecurityTokenExchangeClient(
        authorizedClientManager, registrationId != null ? registrationId : "token-exchange");
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenCachePort tokenCachePort(final CamundaAuthProperties properties) {
    final var cacheProps = properties.getTokenExchange().getCache();
    if (!cacheProps.isEnabled()) {
      return new NoOpTokenCache();
    }
    return new InMemoryTokenCache(cacheProps.getMaxSize(), cacheProps.getTtl());
  }

  @Bean
  @ConditionalOnMissingBean
  public DelegationChainValidator delegationChainValidator(
      final CamundaAuthProperties properties) {
    return new DelegationChainValidator(properties.getObo().getMaxDelegationChainDepth());
  }

  @Bean
  @ConditionalOnMissingBean
  public TokenExchangePort tokenExchangePort(
      final TokenExchangeClient client,
      final TokenCachePort cachePort,
      @Autowired(required = false) final TokenStorePort tokenStorePort,
      final DelegationChainValidator chainValidator) {
    return new TokenExchangeService(client, cachePort, tokenStorePort, chainValidator);
  }

  /** No-op cache implementation when caching is disabled. */
  private static final class NoOpTokenCache implements TokenCachePort {
    @Override
    public Optional<io.camunda.auth.domain.model.TokenExchangeResponse> get(
        final String cacheKey) {
      return Optional.empty();
    }

    @Override
    public void put(
        final String cacheKey,
        final io.camunda.auth.domain.model.TokenExchangeResponse response) {}

    @Override
    public void evictBySubject(final String subjectId) {}

    @Override
    public void evictAll() {}
  }
}
