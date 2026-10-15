/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("dl-nightly")
class NightlyJvmConfigurationIT {

  @ParameterizedTest
  @ValueSource(strings = {"java.io", "jdk.internal.misc"})
  void shouldAllowAccessToRequiredJdkInternals(final String packageName) {
    // given
    final var javaBase = Object.class.getModule();
    final var testModule = getClass().getModule();

    // when
    final var isOpen = javaBase.isOpen(packageName, testModule);

    // then
    assertThat(isOpen).as("java.base/%s should be open to the test JVM", packageName).isTrue();
  }
}
