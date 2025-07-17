/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.interceptor.SecondaryStorageInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Configuration for REST API interceptors. */
@Configuration
public class SecondaryStorageConfig implements WebMvcConfigurer {

  private final SecondaryStorageInterceptor secondaryStorageInterceptor;

  public SecondaryStorageConfig(final SecondaryStorageInterceptor secondaryStorageInterceptor) {
    this.secondaryStorageInterceptor = secondaryStorageInterceptor;
  }

  @Override
  public void addInterceptors(final InterceptorRegistry registry) {
    registry.addInterceptor(secondaryStorageInterceptor);
  }
}
