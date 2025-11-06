/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.util;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** Utility class for loading OpenAPI specifications from YAML files. */
public final class OpenApiYamlLoader {

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiYamlLoader.class);

  // Prevent instantiation
  private OpenApiYamlLoader() {}

  /**
   * Loads an OpenAPI specification from a YAML file located in the classpath.
   *
   * @param yamlPath the path to the YAML file relative to the classpath
   * @return the parsed OpenAPI specification
   * @throws OpenApiLoadingException if the YAML file cannot be loaded or parsed
   */
  public static OpenAPI loadOpenApiFromYaml(final String yamlPath) {
    try {
      final SwaggerParseResult result = loadYamlContent(yamlPath);
      if (result.getOpenAPI() == null) {
        final String errorMsg = "Failed to parse OpenAPI YAML: " + yamlPath;
        if (result.getMessages() != null && !result.getMessages().isEmpty()) {
          LOG.error("{}, Parse errors: {}", errorMsg, result.getMessages());
        }
        throw new OpenApiLoadingException(errorMsg);
      }

      LOG.debug("Successfully loaded OpenAPI specification from: {}", yamlPath);
      return result.getOpenAPI();
    } catch (final IOException e) {
      final String errorMsg = "Failed to load OpenAPI YAML file: " + yamlPath;
      LOG.error(errorMsg, e);
      throw new OpenApiLoadingException(errorMsg, e);
    }
  }

  /**
   * Customizes an OpenAPI specification by loading configuration from a YAML file and applying it
   * to the target OpenAPI instance.
   *
   * @param targetOpenApi the OpenAPI instance to customize
   * @param yamlPath the path to the YAML file containing the configuration
   * @throws OpenApiLoadingException if the YAML file cannot be loaded or parsed
   */
  public static void customizeOpenApiFromYaml(final OpenAPI targetOpenApi, final String yamlPath) {
    try {
      final OpenAPI yamlOpenApi = loadOpenApiFromYaml(yamlPath);

      if (yamlOpenApi.getTags() != null) {
        targetOpenApi.setTags(yamlOpenApi.getTags());
      }

      if (yamlOpenApi.getPaths() != null) {
        final var v2Paths = new io.swagger.v3.oas.models.Paths();
        yamlOpenApi
            .getPaths()
            .forEach(
                (pathKey, pathItem) -> {
                  v2Paths.addPathItem("/v2" + pathKey, pathItem);
                });
        targetOpenApi.setPaths(v2Paths);
      }

      if (yamlOpenApi.getComponents() != null) {
        targetOpenApi.setComponents(yamlOpenApi.getComponents());
      }

      LOG.debug("Successfully customized OpenAPI specification from: {}", yamlPath);
    } catch (final OpenApiLoadingException e) {
      LOG.warn(
          "Could not load OpenAPI from {}, using controller-based organization: {}",
          yamlPath,
          e.getMessage());
      throw e;
    }
  }

  private static SwaggerParseResult loadYamlContent(final String yamlPath) throws IOException {
    final ParseOptions options = new ParseOptions();
    options.setResolve(true);
    options.setResolveFully(true);

    final Path absolutePath = Path.of(yamlPath);

    if (absolutePath.isAbsolute() && Files.exists(absolutePath)) {
      // Load from absolute file path - convert to file:// URL
      LOG.debug("Loading OpenAPI from absolute path: {}", yamlPath);
      final String fileUrl = absolutePath.toUri().toString();
      return new OpenAPIV3Parser().readLocation(fileUrl, null, options);
    }
    // Load from classpath - need to resolve references using classpath resources
    LOG.debug("Loading OpenAPI from classpath: {}", yamlPath);
    final ClassPathResource resource = new ClassPathResource(yamlPath);

    // Get the URL of the resource to use as base for reference resolution
    final String resourceUrl = resource.getURL().toString();
    return new OpenAPIV3Parser().readLocation(resourceUrl, null, options);
  }

  /** Custom exception for OpenAPI loading failures. */
  public static class OpenApiLoadingException extends RuntimeException {
    public OpenApiLoadingException(final String message) {
      super(message);
    }

    public OpenApiLoadingException(final String message, final Throwable cause) {
      super(message, cause);
    }
  }
}
