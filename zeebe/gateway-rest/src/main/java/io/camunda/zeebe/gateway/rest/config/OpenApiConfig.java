/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public GroupedOpenApi zeebeApi() {
    return GroupedOpenApi.builder()
        .group("Camunda 8 API")
        .addOpenApiCustomizer(
            openApi -> {
              try {
                openApi
                    .info(
                        new Info()
                            .title("Camunda 8 API")
                            .version("1.0")
                            .description(
                                "The Camunda 8 REST API is a REST API designed to interact with a Camunda 8 cluster.")
                            .license(
                                new License()
                                    .name("Camunda License 1.0")
                                    .url("https://camunda.com/legal/")))
                    .externalDocs(
                        new ExternalDocumentation()
                            .description("Camunda 8 API Documentation")
                            .url("classpath:proto/rest-api.yaml"));
              } catch (final Exception e) {
                throw new RuntimeException("Error to load Camunda OpenAPI YAML", e);
              }
            })
        .build();
  }
}
