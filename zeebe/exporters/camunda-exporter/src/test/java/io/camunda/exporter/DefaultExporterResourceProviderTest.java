/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.exporter.config.ExporterConfiguration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

public class DefaultExporterResourceProviderTest {
  @Test
  void shouldHaveCorrectDetailsForDefaultEmptyPrefix() {
    final var config = new ExporterConfiguration();
    final var provider = new DefaultExporterResourceProvider();

    provider.init(config);

    provider
        .getIndexDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getFullQualifiedName();
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(startsWithValidComponent(name)).isTrue();
            });

    provider
        .getIndexTemplateDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getTemplateName();
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(startsWithValidComponent(name)).isTrue();
            });
  }

  @Test
  void shouldUseGivenIndexPrefixInRegexAndNameDetails() {
    final var config = new ExporterConfiguration();
    config.getIndex().setPrefix("global");
    final var provider = new DefaultExporterResourceProvider();

    provider.init(config);

    provider
        .getIndexDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getFullQualifiedName();
              final var nameWithoutPrefix = name.substring("global-".length());
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(name.startsWith("global-")).isTrue();
              assertThat(startsWithValidComponent(nameWithoutPrefix)).isTrue();
            });

    provider
        .getIndexTemplateDescriptors()
        .forEach(
            descriptor -> {
              final var name = descriptor.getTemplateName();
              final var nameWithoutPrefix = name.substring("global-".length());
              assertThat(name.matches(descriptor.getAllVersionsIndexNameRegexPattern())).isTrue();
              assertThat(name.startsWith("global-")).isTrue();
              assertThat(startsWithValidComponent(nameWithoutPrefix)).isTrue();
            });
  }

  private boolean startsWithValidComponent(final String str) {
    return Stream.of("operate", "tasklist").anyMatch(str::startsWith);
  }
}
