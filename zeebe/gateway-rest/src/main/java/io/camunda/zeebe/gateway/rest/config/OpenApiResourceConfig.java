/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader.OpenApiLoadingException;
import io.camunda.zeebe.gateway.rest.util.YamlToJsonResourceTransformer;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnRestGatewayEnabled
@ConditionalOnProperty(
    name = "camunda.rest.swagger.enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(OpenApiConfigurationProperties.class)
public class OpenApiResourceConfig implements WebMvcConfigurer {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiResourceConfig.class);

  @Autowired private ObjectMapper objectMapper;
  @Autowired private OpenApiConfigurer openApiConfigurer;

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
    // Load base OpenAPI specification from YAML
    loadBaseOpenApiSpec(openApi);

    // Set API description and configure security based on deployment type
    openApi.info(new Info().description(openApiConfigurer.getApiDescription()));
    openApiConfigurer.configureSecurity(openApi);
  }

  private void loadBaseOpenApiSpec(final OpenAPI openApi) {
    try {
      final OpenAPI yamlOpenApi = OpenApiYamlLoader.loadOpenApiFromYaml("apidoc/rest-api.yaml");

      if (yamlOpenApi.getTags() != null) {
        openApi.setTags(yamlOpenApi.getTags());
      }

      if (yamlOpenApi.getPaths() != null) {
        final var v2Paths = new io.swagger.v3.oas.models.Paths();
        yamlOpenApi
            .getPaths()
            .forEach(
                (pathKey, pathItem) -> {
                  v2Paths.addPathItem("/v2" + pathKey, pathItem);
                });
        openApi.setPaths(v2Paths);
      }

      if (yamlOpenApi.getComponents() != null) {
        openApi.setComponents(yamlOpenApi.getComponents());
      }
    } catch (final OpenApiLoadingException e) {
      LOGGER.warn(
          "Could not load OpenAPI from rest-api.yaml, using controller-based organization: {}",
          e.getMessage());
    }
  }
}
