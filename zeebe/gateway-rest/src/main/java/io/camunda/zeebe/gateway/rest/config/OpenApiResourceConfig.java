/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import io.camunda.zeebe.gateway.rest.ConditionalOnRestGatewayEnabled;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader.OpenApiLoadingException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.system.ApplicationHome;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnRestGatewayEnabled
@ConditionalOnProperty(
    name = "camunda.rest.swagger.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OpenApiResourceConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenApiResourceConfig.class);

  private final OpenApiConfigurer openApiConfigurer;

  public OpenApiResourceConfig(final OpenApiConfigurer openApiConfigurer) {
    this.openApiConfigurer = openApiConfigurer;
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

    // Set API info and configure security based on deployment type
    openApi.info(
        new Info()
            .title("Orchestration Cluster API")
            .version("v2")
            .contact(new Contact().url("https://www.camunda.com"))
            .license(
                new License()
                    .name("License")
                    .url("https://docs.camunda.io/docs/reference/licenses/"))
            .description(openApiConfigurer.getApiDescription()));
    openApiConfigurer.configureSecurity(openApi);
  }

  private void loadBaseOpenApiSpec(final OpenAPI openApi) {
    try {
      OpenApiYamlLoader.customizeOpenApiFromYaml(openApi, resolveSpecPath());
    } catch (final OpenApiLoadingException e) {
      LOGGER.warn(
          "Could not load OpenAPI spec, using controller-based organization: {}", e.getMessage());
    }
  }

  private static String resolveSpecPath() {
    final Path specPath = Path.of(OpenApiYamlLoader.DEFAULT_SPEC_PATH);
    if (Files.isRegularFile(specPath)) {
      return specPath.toString();
    }

    final Path appDir = new ApplicationHome(OpenApiResourceConfig.class).getDir().toPath();
    return resolveSpecPath(appDir);
  }

  static String resolveSpecPath(final Path appDir) {
    // When running as a packaged app, resolve relative to the installation directory.
    // e.g. app JAR lives in $CAMUNDA_HOME/lib -> spec lives in $CAMUNDA_HOME/config/openapi/v2
    final Path installedSpecFromAppDir = appDir.resolve(OpenApiYamlLoader.DEFAULT_SPEC_PATH);
    if (Files.isRegularFile(installedSpecFromAppDir)) {
      return installedSpecFromAppDir.toString();
    }

    final Path parentDir = appDir.getParent();
    if (parentDir != null) {
      final Path installedSpecFromParent = parentDir.resolve(OpenApiYamlLoader.DEFAULT_SPEC_PATH);
      if (Files.isRegularFile(installedSpecFromParent)) {
        return installedSpecFromParent.toString();
      }
    }

    // Development fallback (e.g. running tests/IDE)
    return OpenApiYamlLoader.DEFAULT_CLASSPATH_SPEC_PATH;
  }
}
