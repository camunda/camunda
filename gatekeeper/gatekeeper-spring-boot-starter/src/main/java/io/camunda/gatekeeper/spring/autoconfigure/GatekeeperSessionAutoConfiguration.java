/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.autoconfigure;

import io.camunda.gatekeeper.spi.SessionPersistencePort;
import io.camunda.gatekeeper.spring.condition.ConditionalOnPersistentWebSessionEnabled;
import io.camunda.gatekeeper.spring.session.WebSessionAttributeConverter;
import io.camunda.gatekeeper.spring.session.WebSessionDeletionTask;
import io.camunda.gatekeeper.spring.session.WebSessionMapper;
import io.camunda.gatekeeper.spring.session.WebSessionMapper.SpringBasedWebSessionAttributeConverter;
import io.camunda.gatekeeper.spring.session.WebSessionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.convert.support.GenericConversionService;

/**
 * Auto-configuration for persistent web session management. Only active when persistent session
 * support is enabled via configuration properties.
 */
@AutoConfiguration(after = GatekeeperAuthAutoConfiguration.class)
@ConditionalOnPersistentWebSessionEnabled
public final class GatekeeperSessionAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public WebSessionMapper webSessionMapper(
      final Optional<WebSessionAttributeConverter> converterOptional) {
    final var converter =
        converterOptional.orElseGet(
            () -> new SpringBasedWebSessionAttributeConverter(new GenericConversionService()));
    return new WebSessionMapper(converter);
  }

  @Bean
  @ConditionalOnMissingBean
  public WebSessionRepository webSessionRepository(
      final SessionPersistencePort sessionPersistencePort,
      final WebSessionMapper webSessionMapper,
      final HttpServletRequest request) {
    return new WebSessionRepository(sessionPersistencePort, webSessionMapper, request);
  }

  @Bean
  @ConditionalOnMissingBean
  public WebSessionDeletionTask webSessionDeletionTask(
      final WebSessionRepository webSessionRepository) {
    return new WebSessionDeletionTask(webSessionRepository);
  }
}
