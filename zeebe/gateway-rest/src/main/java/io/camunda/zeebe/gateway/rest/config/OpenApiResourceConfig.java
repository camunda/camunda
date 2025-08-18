/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader.OpenApiLoadingException;
import io.camunda.zeebe.gateway.rest.util.YamlToJsonResourceTransformer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class OpenApiResourceConfig implements WebMvcConfigurer {

  public static final String BEARER_SECURITY_SCHEMA_NAME = "bearerAuth";
  public static final SecurityScheme BEARER_SECURITY_SCHEMA =
      new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT");
  private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiResourceConfig.class);
  @Autowired private ObjectMapper objectMapper;

  @Override
  public void addResourceHandlers(final ResourceHandlerRegistry registry) {
    registry
        .addResourceHandler("/rest-api.yaml")
        .addResourceLocations("classpath:/apidoc/")
        .resourceChain(true)
        .addTransformer(new YamlToJsonResourceTransformer(objectMapper));
  }

  @Bean
  public GroupedOpenApi restApiV2() {
    return GroupedOpenApi.builder()
        .group("Orchestration Cluster API")
        .addOpenApiCustomizer(this::customizeOpenApi)
        .pathsToMatch("/v2/**")
        .build();
  }

  private void customizeOpenApi(final OpenAPI openApi) {
    try {
      OpenApiYamlLoader.customizeOpenApiFromYaml(openApi, "apidoc/rest-api.yaml");
    } catch (final OpenApiLoadingException e) {
      LOGGER.warn(
          "Could not load OpenAPI from rest-api.yaml, using controller-based organization: {}",
          e.getMessage());
    }

    openApi
        .info(new Info().description(getApiDescription()))
        .getComponents()
        .addSecuritySchemes(BEARER_SECURITY_SCHEMA_NAME, BEARER_SECURITY_SCHEMA);

    openApi.addSecurityItem(new SecurityRequirement().addList(BEARER_SECURITY_SCHEMA_NAME));
  }

  private String getApiDescription() {
    return "API for communicating with a Camunda 8 cluster.";
  }
}
