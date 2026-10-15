/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.active;

import io.camunda.authentication.source.LoginSources;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.api.model.config.AuthenticationMethod;
import io.camunda.security.spring.annotation.ConditionalOnAuthenticationMethod;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.spring.utils.ConditionalOnSecondaryStorageEnabled;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Enforces {@code active=false} for OIDC users, whose identity is otherwise managed by the IdP.
 * Requires secondary storage, where the flag is read from.
 */
@Configuration
@ConditionalOnAuthenticationMethod(AuthenticationMethod.OIDC)
@ConditionalOnSecondaryStorageEnabled
public class InactiveUserFilterConfiguration {

  public static final String CACHE_TTL_PROPERTY = "camunda.security.active-user-check.cache-ttl";

  static final Duration DEFAULT_CACHE_TTL = Duration.ofSeconds(30);

  // Spring Security's FilterChainProxy is registered at order -100; this filter must run inside it
  // to see the SecurityContext. The value is inlined to avoid a dependency on spring-boot-security
  // for one constant, as ApiFiltersConfiguration does.
  private static final int ORDER_AFTER_SECURITY_CHAIN = -99;

  @Bean
  public ActiveUserChecker activeUserChecker(
      final ObjectProvider<ServiceRegistry> serviceRegistry, final Environment environment) {
    final var ttl =
        Binder.get(environment).bind(CACHE_TTL_PROPERTY, Duration.class).orElse(DEFAULT_CACHE_TTL);
    return new ActiveUserChecker(serviceRegistry::getObject, ttl);
  }

  @Bean
  public FilterRegistrationBean<InactiveUserFilter> inactiveUserFilter(
      final CamundaAuthenticationProvider authenticationProvider,
      final ActiveUserChecker activeUserChecker,
      final Environment environment) {
    final var registration =
        new FilterRegistrationBean<>(
            new InactiveUserFilter(
                authenticationProvider, activeUserChecker, LoginSources.from(environment)));
    registration.addUrlPatterns("/*");
    registration.setOrder(ORDER_AFTER_SECURITY_CHAIN);
    return registration;
  }
}
