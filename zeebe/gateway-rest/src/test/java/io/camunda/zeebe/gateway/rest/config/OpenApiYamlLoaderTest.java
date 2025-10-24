/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader;
import io.camunda.zeebe.gateway.rest.util.OpenApiYamlLoader.OpenApiLoadingException;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.tags.Tag;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiYamlLoaderTest {

  @TempDir Path tempDir;

  @Test
  void shouldLoadValidYamlFile() throws IOException {
    // given
    final String validYaml =
        """
        openapi: "3.0.3"
        info:
          title: Test API
          version: "1.0"
        tags:
          - name: Authentication
          - name: Authorization
        paths:
          /test:
            get:
              tags:
                - Authentication
              operationId: test
              responses:
                "200":
                  description: Success
        components:
          schemas:
            TestSchema:
              type: object
              properties:
                id:
                  type: string
        """;

    final Path yamlFile = tempDir.resolve("test-api.yaml");
    Files.writeString(yamlFile, validYaml);

    // when
    final OpenAPI result = OpenApiYamlLoader.loadOpenApiFromYaml(yamlFile.toString());

    // then
    assertThat(result).isNotNull();
    assertThat(result.getInfo().getTitle()).isEqualTo("Test API");
    assertThat(result.getTags())
        .hasSize(2)
        .extracting(Tag::getName)
        .containsExactly("Authentication", "Authorization");
    assertThat(result.getPaths()).hasSize(1).containsKey("/test");
    assertThat(result.getComponents().getSchemas()).hasSize(1).containsKey("TestSchema");
  }

  @Test
  void shouldThrowExceptionForNonExistentFile() {
    // given
    final String nonExistentPath = "non-existent/path/to/file.yaml";

    // when/then
    assertThatThrownBy(() -> OpenApiYamlLoader.loadOpenApiFromYaml(nonExistentPath))
        .isInstanceOf(OpenApiLoadingException.class)
        .hasMessageContaining("Failed to load OpenAPI YAML file")
        .hasCauseInstanceOf(IOException.class);
  }

  @Test
  void shouldThrowExceptionForMalformedYaml() throws IOException {
    // given
    final String malformedYaml =
        """
        openapi: "3.0.3
        info:
          title: Test API
          version: "1.0
        this is not valid yaml syntax
        """;

    final Path yamlFile = tempDir.resolve("malformed-api.yaml");
    Files.writeString(yamlFile, malformedYaml);

    // when/then
    assertThatThrownBy(() -> OpenApiYamlLoader.loadOpenApiFromYaml(yamlFile.toString()))
        .isInstanceOf(OpenApiLoadingException.class)
        .hasMessageContaining("Failed to parse OpenAPI YAML");
  }

  @Test
  void shouldLoadYamlWithMinimalContent() throws IOException {
    // given
    final String minimalYaml =
        """
        openapi: "3.0.3"
        info:
          title: Minimal API
          version: "1.0"
        paths: {}
        """;

    final Path yamlFile = tempDir.resolve("minimal-api.yaml");
    Files.writeString(yamlFile, minimalYaml);

    // when
    final OpenAPI result = OpenApiYamlLoader.loadOpenApiFromYaml(yamlFile.toString());

    // then
    assertThat(result).isNotNull();
    assertThat(result.getInfo().getTitle()).isEqualTo("Minimal API");
    assertThat(result.getPaths()).isEmpty();
    assertThat(result.getTags()).isNull();
    assertThat(result.getComponents()).isNull();
  }

  @Test
  void shouldHandleComplexYamlStructure() throws IOException {
    // given
    final String complexYaml =
        """
        openapi: "3.0.3"
        info:
          title: Complex API
          version: "2.0"
          description: A complex API specification
        servers:
          - url: "{schema}://{host}:{port}/v2"
            variables:
              host:
                default: localhost
              port:
                default: "8080"
              schema:
                default: http
        tags:
          - name: User
            description: User management operations
          - name: Admin
            description: Administrative operations
        paths:
          /users:
            get:
              tags:
                - User
              operationId: getUsers
              responses:
                "200":
                  description: List of users
                  content:
                    application/json:
                      schema:
                        type: array
                        items:
                          $ref: "#/components/schemas/User"
            post:
              tags:
                - User
              operationId: createUser
              requestBody:
                content:
                  application/json:
                    schema:
                      $ref: "#/components/schemas/CreateUserRequest"
              responses:
                "201":
                  description: User created
          /admin/settings:
            get:
              tags:
                - Admin
              operationId: getSettings
              responses:
                "200":
                  description: Settings
        components:
          schemas:
            User:
              type: object
              properties:
                id:
                  type: string
                name:
                  type: string
                email:
                  type: string
            CreateUserRequest:
              type: object
              required:
                - name
                - email
              properties:
                name:
                  type: string
                email:
                  type: string
          responses:
            NotFound:
              description: Resource not found
          parameters:
            UserId:
              name: userId
              in: path
              required: true
              schema:
                type: string
        """;

    final Path yamlFile = tempDir.resolve("complex-api.yaml");
    Files.writeString(yamlFile, complexYaml);

    // when
    final OpenAPI result = OpenApiYamlLoader.loadOpenApiFromYaml(yamlFile.toString());

    // then
    assertThat(result).isNotNull();
    assertThat(result.getInfo().getTitle()).isEqualTo("Complex API");
    assertThat(result.getInfo().getDescription()).isEqualTo("A complex API specification");
    assertThat(result.getServers()).hasSize(1);
    assertThat(result.getTags())
        .hasSize(2)
        .extracting(Tag::getName)
        .containsExactly("User", "Admin");
    assertThat(result.getPaths()).hasSize(2).containsKeys("/users", "/admin/settings");
    assertThat(result.getComponents().getSchemas())
        .hasSize(2)
        .containsKeys("User", "CreateUserRequest");
    assertThat(result.getComponents().getResponses()).hasSize(1).containsKey("NotFound");
    assertThat(result.getComponents().getParameters()).hasSize(1).containsKey("UserId");
  }

  @Test
  void openApiLoadingExceptionShouldHaveCorrectMessageAndCause() {
    // given
    final String message = "Test error message";
    final IOException cause = new IOException("IO error");

    // when
    final OpenApiLoadingException exceptionWithCause = new OpenApiLoadingException(message, cause);
    final OpenApiLoadingException exceptionWithoutCause = new OpenApiLoadingException(message);

    // then
    assertThat(exceptionWithCause.getMessage()).isEqualTo(message);
    assertThat(exceptionWithCause.getCause()).isEqualTo(cause);

    assertThat(exceptionWithoutCause.getMessage()).isEqualTo(message);
    assertThat(exceptionWithoutCause.getCause()).isNull();
  }
}
