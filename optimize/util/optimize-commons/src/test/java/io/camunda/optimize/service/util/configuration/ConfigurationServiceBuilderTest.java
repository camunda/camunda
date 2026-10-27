/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.ConfigEnvironment;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.env.AbstractEnvironment;
import org.springframework.core.env.PropertiesPropertySource;

class ConfigurationServiceBuilderTest {

  @Nested
  class ConfigEnvironmentTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("configLocationCases")
    void shouldResolveConfigLocationsFromEnvironmentAsCommaSeparatedList(
        final String caseName,
        final List<String> importFiles,
        final List<String> locationPaths,
        final List<String> expectedLocations) {
      // given
      final FakeEnvironment environment = new FakeEnvironment();
      if (importFiles != null) {
        environment.setProperty("spring.config.import", String.join(",", importFiles));
      }
      if (locationPaths != null) {
        environment.setProperty("spring.config.location", String.join(",", locationPaths));
      }

      // when
      final var configLocations = ConfigEnvironment.resolveConfigLocations(environment);

      // then
      Assertions.assertThat(configLocations).containsExactlyElementsOf(expectedLocations);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configLocationCases")
    void shouldResolveConfigLocationsFromEnvironmentAsCommaSeparatedListWithWhitespace(
        final String caseName,
        final List<String> importFiles,
        final List<String> locationPaths,
        final List<String> expectedLocations) {
      // given
      final FakeEnvironment environment = new FakeEnvironment();
      if (importFiles != null) {
        environment.setProperty("spring.config.import", String.join(" , ", importFiles));
      }
      if (locationPaths != null) {
        environment.setProperty("spring.config.location", String.join(" , ", locationPaths));
      }

      // when
      final var configLocations = ConfigEnvironment.resolveConfigLocations(environment);

      // then
      Assertions.assertThat(configLocations).containsExactlyElementsOf(expectedLocations);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("configLocationCases")
    void shouldResolveConfigLocationsFromEnvironmentAsIndexedProperties(
        final String caseName,
        final List<String> importFiles,
        final List<String> locationPaths,
        final List<String> expectedLocations) {
      // given
      final FakeEnvironment environment = new FakeEnvironment();
      if (importFiles != null) {
        for (var i = 0; i < importFiles.size(); i++) {
          environment.setProperty("spring.config.import[" + i + "]", importFiles.get(i));
        }
      }
      if (locationPaths != null) {
        for (var i = 0; i < locationPaths.size(); i++) {
          environment.setProperty("spring.config.location[" + i + "]", locationPaths.get(i));
        }
      }

      // when
      final var configLocations = ConfigEnvironment.resolveConfigLocations(environment);

      // then
      Assertions.assertThat(configLocations).containsExactlyElementsOf(expectedLocations);
    }

    private static Stream<Arguments> configLocationCases() {
      return Stream.of(
          Arguments.arguments("no import files or locations", null, null, List.of()),
          Arguments.arguments(
              "only import files",
              List.of("config-from-import.yaml", "another-config.yaml"),
              null,
              List.of("config-from-import.yaml", "another-config.yaml")),
          Arguments.arguments(
              "only locations", null, List.of("my-path/", "another-path/"), List.of()),
          Arguments.arguments(
              "single import",
              List.of("config-from-import.yaml"),
              List.of("my-path/"),
              List.of("my-path/config-from-import.yaml")),
          Arguments.arguments(
              "multiple files in single import",
              List.of("config-from-import.yaml", "another-config.yaml"),
              List.of("my-path/"),
              List.of("my-path/config-from-import.yaml", "my-path/another-config.yaml")),
          Arguments.arguments(
              "single file in multiple locations",
              List.of("config-from-import.yaml"),
              List.of("my-path/", "another-path/"),
              List.of("my-path/config-from-import.yaml", "another-path/config-from-import.yaml")),
          Arguments.arguments(
              "multiple files in multiple locations",
              List.of("config-from-import.yaml", "another-config.yaml"),
              List.of("my-path/", "another-path/"),
              List.of(
                  "my-path/config-from-import.yaml",
                  "another-path/config-from-import.yaml",
                  "my-path/another-config.yaml",
                  "another-path/another-config.yaml")),
          Arguments.arguments(
              "absolute path as file in import should be added as is",
              List.of("/absolute/path/config-from-import.yaml"),
              List.of("path-should-be-ignored/"),
              List.of("/absolute/path/config-from-import.yaml")),
          Arguments.arguments(
              "optional prefix should be ignored",
              List.of("optional:config-from-import.yaml"),
              List.of("my-path/"),
              List.of("my-path/config-from-import.yaml")),
          Arguments.arguments(
              "optional prefix with absolute path should be ignored and path should be added as is",
              List.of("optional:/absolute/path/config-from-import.yaml"),
              List.of("path-should-be-ignored/"),
              List.of("/absolute/path/config-from-import.yaml")),
          Arguments.arguments(
              "file protocol with absolute path in import should be added as absolute file path",
              List.of("file:/absolute/path/config-from-import.yaml"),
              List.of("path-should-be-ignored/"),
              List.of("/absolute/path/config-from-import.yaml")),
          Arguments.arguments(
              "optional prefix with file protocol and absolute path in import should be absolute",
              List.of("optional:file:/absolute/path/config-from-import.yaml"),
              List.of("path-should-be-ignored/"),
              List.of("/absolute/path/config-from-import.yaml")),
          Arguments.arguments(
              "optional prefix with file protocol and relative path should be resolved relatively",
              List.of("optional:file:config-from-import.yaml"),
              List.of("my-path/"),
              List.of("my-path/config-from-import.yaml")),
          Arguments.arguments(
              "multiple absolute paths in import should be added as is",
              List.of(
                  "/absolute/path/config-from-import.yaml",
                  "/another-absolute/path/another-config.yaml"),
              List.of("path-should-be-ignored/"),
              List.of(
                  "/absolute/path/config-from-import.yaml",
                  "/another-absolute/path/another-config.yaml")),
          Arguments.arguments(
              "multiple absolute paths with optional:file: prefix should be added as is",
              List.of(
                  "optional:file:/absolute/path/config-from-import.yaml",
                  "optional:file:/another-absolute/path/another-config.yaml"),
              List.of("path-should-be-ignored/"),
              List.of(
                  "/absolute/path/config-from-import.yaml",
                  "/another-absolute/path/another-config.yaml")),
          Arguments.arguments(
              "empty file in import should be ignored",
              List.of("config-from-import.yaml", "", "another-config.yaml"),
              List.of("my-path/"),
              List.of("my-path/config-from-import.yaml", "my-path/another-config.yaml")),
          Arguments.arguments(
              "empty path in location should be ignored",
              List.of("config-from-import.yaml"),
              List.of("my-path/", "", "another-path/"),
              List.of("my-path/config-from-import.yaml", "another-path/config-from-import.yaml")));
    }

    private static class FakeEnvironment extends AbstractEnvironment {
      private final MockPropertySource propertySource =
          new MockPropertySource("props", new Properties());

      public FakeEnvironment() {
        getPropertySources().addLast(propertySource);
      }

      public void setProperty(final String name, final Object value) {
        propertySource.setProperty(name, value);
      }
    }

    private static class MockPropertySource extends PropertiesPropertySource {

      public MockPropertySource(final String name, final Properties properties) {
        super(name, properties);
      }

      public void setProperty(final String name, final Object value) {
        if (name != null && value != null) {
          source.put(name, value);
        }
      }
    }
  }
}
