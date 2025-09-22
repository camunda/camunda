/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.configuration.processor.CamundaLegacyPropertiesMapping;
import io.camunda.configuration.processor.CamundaLegacyPropertiesMappingLoader;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

public class CamundaLegacyPropertiesMappingLoaderTest {
  @Test
  void shouldLoad() {
    final List<CamundaLegacyPropertiesMapping> load = CamundaLegacyPropertiesMappingLoader.load();
    assertThat(load).isNotNull().isNotEmpty();
  }

  @TestFactory
  Stream<DynamicContainer> mappingTests() {
    return CamundaLegacyPropertiesMappingLoader.load().stream()
        .map(
            mapping ->
                DynamicContainer.dynamicContainer(
                    mapping.newProperty(), singleMappingTests(mapping)));
  }

  private Stream<? extends DynamicNode> singleMappingTests(
      final CamundaLegacyPropertiesMapping mapping) {
    return Stream.of(
        DynamicTest.dynamicTest(
            "Should have no duplicates",
            () ->
                assertThat(
                        CamundaLegacyPropertiesMappingLoader.load().stream()
                            .map(CamundaLegacyPropertiesMapping::newProperty)
                            .toList())
                    .containsOnlyOnce(mapping.newProperty())),
        DynamicContainer.dynamicContainer(
            "Should have profiles with multiple legacy properties set",
            mapping.legacyProperties().stream()
                .filter(legacyProperties -> legacyProperties.size() > 1)
                .flatMap(
                    legacyProperties ->
                        legacyProperties.stream()
                            .map(
                                property ->
                                    DynamicTest.dynamicTest(
                                        property.name(),
                                        () ->
                                            assertThat(property.profiles())
                                                .isNotNull()
                                                .isNotEmpty())))));
  }
}
