/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.interceptor.LongPollingDisconnectInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Registers the {@link LongPollingDisconnectInterceptor} which periodically probes long-poll
 * connections to detect client disconnects during job activation. When long polling is disabled,
 * the interceptor is harmless since there are no pending long-poll requests to probe.
 */
@Configuration
public class LongPollingDisconnectConfig implements WebMvcConfigurer {

  @Bean
  LongPollingDisconnectInterceptor longPollingDisconnectInterceptor() {
    return new LongPollingDisconnectInterceptor();
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry
        .addInterceptor(longPollingDisconnectInterceptor())
        .addPathPatterns("/v2/jobs/activation");
  }
}
