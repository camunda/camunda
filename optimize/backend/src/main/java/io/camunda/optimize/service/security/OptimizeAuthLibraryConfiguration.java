/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.security;

import io.camunda.auth.domain.config.TokenConfiguration;
import io.camunda.auth.domain.model.CookieDescriptor;
import io.camunda.auth.domain.model.SameSitePolicy;
import io.camunda.auth.domain.spi.CookiePathResolver;
import io.camunda.auth.domain.spi.SessionTokenService;
import io.camunda.auth.domain.spi.TerminatedSessionPort;
import io.camunda.auth.spring.filter.CookieAuthenticationFilter;
import io.camunda.auth.spring.session.ChunkedCookieService;
import io.camunda.auth.spring.session.JwtSessionTokenService;
import io.camunda.optimize.service.metadata.Version;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.security.AuthConfiguration;
import io.camunda.optimize.service.util.configuration.security.CookieConfiguration;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OptimizeAuthLibraryConfiguration {

  private static final Logger LOG = LoggerFactory.getLogger(OptimizeAuthLibraryConfiguration.class);
  private static final long CLEANUP_INTERVAL_HOURS = 8;

  @Bean
  CookieDescriptor cookieDescriptor(final ConfigurationService configurationService) {
    final AuthConfiguration auth = configurationService.getAuthConfiguration();
    final CookieConfiguration cookie = auth.getCookieConfiguration();
    return new CookieDescriptor(
        "X-Optimize-Authorization",
        "/" + auth.getCloudAuthConfiguration().getClusterId(),
        cookie.resolveSecureFlagValue("https"),
        true,
        cookie.isSameSiteFlagEnabled() ? SameSitePolicy.STRICT : SameSitePolicy.NONE,
        cookie.getMaxSize(),
        Duration.ofMinutes(auth.getTokenLifeTimeMinutes()));
  }

  @Bean
  CookiePathResolver cookiePathResolver(final ConfigurationService configurationService) {
    return () ->
        "/"
            + configurationService
                .getAuthConfiguration()
                .getCloudAuthConfiguration()
                .getClusterId();
  }

  @Bean
  ChunkedCookieService chunkedCookieService(
      final CookieDescriptor descriptor, final CookiePathResolver resolver) {
    return new ChunkedCookieService(descriptor, resolver);
  }

  @Bean
  TokenConfiguration tokenConfiguration(
      final ConfigurationService configurationService, final CookieDescriptor descriptor) {
    final AuthConfiguration auth = configurationService.getAuthConfiguration();
    final byte[] secret =
        auth.getTokenSecret()
            .map(s -> s.getBytes(StandardCharsets.UTF_8))
            .orElseGet(
                () -> {
                  LOG.info("No token secret configured; generating a random secret.");
                  final byte[] b = new byte[64];
                  new SecureRandom().nextBytes(b);
                  return b;
                });
    return new TokenConfiguration(
        Duration.ofMinutes(auth.getTokenLifeTimeMinutes()),
        secret,
        "Optimize-" + Version.RAW_VERSION,
        descriptor,
        Duration.ofHours(CLEANUP_INTERVAL_HOURS));
  }

  @Bean
  SessionTokenService sessionTokenService(
      final TokenConfiguration config, final TerminatedSessionPort port) {
    return new JwtSessionTokenService(config, port);
  }

  @Bean
  CookieAuthenticationFilter cookieAuthenticationFilter(
      final SessionTokenService tokenService, final ChunkedCookieService cookieService) {
    return new CookieAuthenticationFilter(tokenService, cookieService);
  }

  @Bean(destroyMethod = "shutdown")
  ScheduledThreadPoolExecutor terminatedSessionCleanupExecutor(
      final TokenConfiguration config, final TerminatedSessionPort port) {
    final ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
    executor.scheduleWithFixedDelay(
        () -> {
          LOG.debug("Cleaning up terminated user sessions.");
          port.deleteOlderThan(Instant.now().minus(config.tokenLifetime()));
        },
        CLEANUP_INTERVAL_HOURS,
        CLEANUP_INTERVAL_HOURS,
        TimeUnit.HOURS);
    return executor;
  }
}
