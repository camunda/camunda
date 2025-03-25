/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.clients.SecondaryDbQueryService;
import io.camunda.search.clients.impl.NoopSecondaryDbQueryService;
import io.camunda.search.clients.impl.SecondaryDbQueryServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SecondaryDbAccessConfiguration {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(SecondaryDbAccessConfiguration.class);

  @Bean
  @ConditionalOnBean(ProcessInstanceSearchClient.class)
  public SecondaryDbQueryService secondaryDbQueryService(
      final ProcessInstanceSearchClient processInstanceSearchClient) {
    return new SecondaryDbQueryServiceImpl(processInstanceSearchClient);
  }

  @Bean
  @ConditionalOnMissingBean(SecondaryDbQueryService.class)
  public SecondaryDbQueryService noopSecondaryDbQueryService() {
    LOGGER.warn("Secondary DB is not enabled on this broker, using noop implementation");
    return new NoopSecondaryDbQueryService();
  }
}
