/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.exporter.cache.ExporterEntityCacheProvider;
import io.camunda.exporter.config.ExporterConfiguration;
import io.camunda.search.test.utils.TestObjectMapper;
import io.camunda.webapps.schema.descriptors.ComponentNames;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class DefaultExporterResourceProviderTest {
  @ParameterizedTest
  @MethodSource("configProvider")
  void shouldHaveCorrectFullQualifiedNamesForIndexAndTemplates(final ExporterConfiguration config) {
    final var provider = new DefaultExporterResourceProvider();

    provider.init(
        config,
        mock(ExporterEntityCacheProvider.class),
        new SimpleMeterRegistry(),
        new ExporterMetadata(TestObjectMapper.objectMapper()),
        TestObjectMapper.objectMapper());

    provider
        .getIndexDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getFullQualifiedName();
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(
                      isValidIndexDescriptorName(descriptor, config.getConnect().getIndexPrefix()))
                  .isTrue();
            });

    provider
        .getIndexTemplateDescriptors()
        .forEach(
            descriptor ->
                assertThat(
                        isValidIndexTemplateDescriptorName(
                            descriptor, config.getConnect().getIndexPrefix()))
                    .isTrue());
  }

  static Stream<ExporterConfiguration> configProvider() {
    final var configNoPrefix = new ExporterConfiguration();

    final var configWithPrefix = new ExporterConfiguration();
    configWithPrefix.getConnect().setIndexPrefix("global");

    final var configWithComponentNameAsPrefix = new ExporterConfiguration();
    configWithComponentNameAsPrefix.getConnect().setIndexPrefix("operate");

    return Stream.of(configNoPrefix, configWithPrefix, configWithComponentNameAsPrefix);
  }

  private boolean isValidIndexDescriptorName(
      final IndexDescriptor descriptor, final String prefix) {

    return Arrays.stream(ComponentNames.values())
        .map(componentName -> expectedName(descriptor, componentName.toString(), prefix) + "_")
        .anyMatch(
            possibleFullQualifiedName ->
                possibleFullQualifiedName.equals(descriptor.getFullQualifiedName()));
  }

  private boolean isValidIndexTemplateDescriptorName(
      final IndexTemplateDescriptor descriptor, final String prefix) {
    return Arrays.stream(ComponentNames.values())
        .map(
            componentName ->
                expectedName(descriptor, componentName.toString(), prefix) + "_template")
        .anyMatch(
            possibleTemplateName -> possibleTemplateName.equals(descriptor.getTemplateName()));
  }

  private String expectedName(
      final IndexDescriptor descriptor, final String componentName, final String prefix) {
    final var expectedName =
        new ArrayDeque<>(
            List.of(componentName, descriptor.getIndexName(), descriptor.getVersion()));

    if (!prefix.isEmpty()) {
      expectedName.addFirst(prefix);
    }

    return String.join("-", expectedName);
  }
}
