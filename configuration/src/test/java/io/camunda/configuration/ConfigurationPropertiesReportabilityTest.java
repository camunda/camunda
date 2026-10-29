/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.util.Arrays;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Spring Boot's {@code /actuator/configprops} drops a nested property (and its whole subtree) when
 * the owning class has no setter matching the getter's type and the nested type lives in a
 * different package than the owner. These tests guard the properties that previously fell into that
 * trap.
 */
final class ConfigurationPropertiesReportabilityTest {

  @ParameterizedTest
  @CsvSource({
    "io.camunda.configuration.beans.BrokerBasedProperties, processing",
    "io.camunda.configuration.processing.flowcontrol.Limit, aimd",
    "io.camunda.configuration.processing.flowcontrol.Limit, fixed",
    "io.camunda.configuration.processing.flowcontrol.Limit, vegas",
    "io.camunda.configuration.processing.flowcontrol.Limit, gradient",
    "io.camunda.configuration.processing.flowcontrol.Limit, gradient2",
    "io.camunda.configuration.processing.flowcontrol.Limit, legacyVegas",
  })
  void shouldHaveMatchingSetterForNestedProperty(final Class<?> owner, final String property)
      throws IntrospectionException {
    // when
    final PropertyDescriptor descriptor =
        Arrays.stream(Introspector.getBeanInfo(owner).getPropertyDescriptors())
            .filter(d -> d.getName().equals(property))
            .findFirst()
            .orElseThrow();

    // then
    assertThat(descriptor.getReadMethod()).isNotNull();
    assertThat(descriptor.getWriteMethod())
        .as("setter for '%s' on %s", property, owner.getSimpleName())
        .isNotNull();
    assertThat(descriptor.getWriteMethod().getParameterTypes())
        .containsExactly(descriptor.getReadMethod().getReturnType());
  }
}
