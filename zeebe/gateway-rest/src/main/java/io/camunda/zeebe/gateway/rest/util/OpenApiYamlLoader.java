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
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/** Utility class for loading OpenAPI specifications from YAML files. */
public final class OpenApiYamlLoader {

  /** Default OpenAPI specification path on the classpath (used as a development fallback). */
  public static final String DEFAULT_CLASSPATH_SPEC_PATH = "v2/rest-api.yaml";

  /**
   * Default OpenAPI specification path in the distribution.
   *
   * <p>The distribution ships the OpenAPI YAMLs as real files under {@code config/openapi/v2} so
   * that relative {@code $ref} resolution works reliably.
   */
  public static final String DEFAULT_SPEC_PATH = "config/openapi/v2/rest-api.yaml";

  private static final Logger LOG = LoggerFactory.getLogger(OpenApiYamlLoader.class);

  // Prevent instantiation
  private OpenApiYamlLoader() {}

  /**
   * Loads an OpenAPI specification from a YAML file.
   *
   * <p>The file can be either on the filesystem (absolute or relative path), or on the classpath.
   *
   * @param yamlPath the path to the YAML file
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
  }

  private static SwaggerParseResult loadYamlContent(final String yamlPath) throws IOException {
    final ParseOptions options = new ParseOptions();
    options.setResolve(true);
    options.setResolveFully(true);

    final Path filePath = Path.of(yamlPath);
    if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
      // Load from file path (absolute or relative) - convert to file:// URL
      LOG.debug("Loading OpenAPI from file path: {}", yamlPath);
      final String fileUrl = filePath.toUri().toString();
      return new OpenAPIV3Parser().readLocation(fileUrl, null, options);
    }
    // Load from classpath
    LOG.debug("Loading OpenAPI from classpath: {}", yamlPath);

    final URL resourceUrl;
    try {
      resourceUrl = new ClassPathResource(yamlPath).getURL();
    } catch (final IOException e) {
      if (DEFAULT_SPEC_PATH.equals(yamlPath)) {
        LOG.debug(
            "OpenAPI spec not found at '{}', falling back to classpath '{}'",
            DEFAULT_SPEC_PATH,
            DEFAULT_CLASSPATH_SPEC_PATH);
        return loadYamlContent(DEFAULT_CLASSPATH_SPEC_PATH);
      }
      throw e;
    }

    return new OpenAPIV3Parser().readLocation(resourceUrl.toString(), null, options);
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
