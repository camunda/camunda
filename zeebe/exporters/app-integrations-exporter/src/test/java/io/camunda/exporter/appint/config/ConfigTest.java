/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.appint.config;

import static io.camunda.exporter.appint.config.ConfigValidator.*;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class ConfigTest {

  @Test
  void testConfigValidation() {
    final Config validConfig = new Config().setUrl("http://example.com");

    // This should not throw an exception
    validate(validConfig);

    final Config invalidConfig1 = new Config().setUrl("");
    org.junit.jupiter.api.Assertions.assertThrows(
        IllegalArgumentException.class, () -> validate(invalidConfig1));
  }

  @Test
  void shouldExposeClusterId() {
    // given
    final Config config = new Config().setUrl("http://example.com").setClusterId("my-cluster");

    // when / then — clusterId is optional and does not affect validation
    validate(config);
    assertThat(config.getClusterId()).isEqualTo("my-cluster");
  }
}
