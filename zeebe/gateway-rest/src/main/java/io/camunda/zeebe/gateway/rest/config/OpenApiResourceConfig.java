/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.rest.util.YamlToJsonResourceTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiResourceConfig implements WebMvcConfigurer {

  @Autowired
  @Qualifier("gatewayRestObjectMapper")
  private ObjectMapper objectMapper;

  @Autowired
  @Qualifier("yamlObjectMapper")
  private ObjectMapper yamlMapper;

  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/rest-api.yaml")
        .addResourceLocations("classpath:/apidoc/")
        .resourceChain(true)
        .addTransformer(new YamlToJsonResourceTransformer(objectMapper, yamlMapper));
  }
}
