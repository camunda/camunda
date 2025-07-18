/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for RestoreCfg configuration class to ensure proper default values and configuration
 * behavior.
 */
final class RestoreCfgTest {

  @Test
  void shouldHaveDefaultIgnoreFiles() {
    // given
    final var config = new RestoreCfg();

    // then
    assertThat(config.getIgnoreFilesInTarget()).containsExactly("lost+found");
  }

  @Test
  void shouldAllowSettingCustomIgnoreFiles() {
    // given
    final var config = new RestoreCfg();
    final var customIgnoreFiles = List.of("lost+found", ".DS_Store", "Thumbs.db");

    // when
    config.setIgnoreFilesInTarget(customIgnoreFiles);

    // then
    assertThat(config.getIgnoreFilesInTarget()).isEqualTo(customIgnoreFiles);
  }

  @Test
  void shouldAllowEmptyIgnoreFilesList() {
    // given
    final var config = new RestoreCfg();

    // when
    config.setIgnoreFilesInTarget(List.of());

    // then
    assertThat(config.getIgnoreFilesInTarget()).isEmpty();
  }

  @Test
  void shouldInitializeWithoutErrors() {
    // given
    final var config = new RestoreCfg();
    final var brokerCfg = new BrokerCfg();

    // when / then - should not throw any exceptions
    config.init(brokerCfg, "test-base");
  }

  @Test
  void shouldIncludeInToString() {
    // given
    final var config = new RestoreCfg();
    config.setIgnoreFilesInTarget(List.of("test-file"));

    // when
    final String toString = config.toString();

    // then
    assertThat(toString).contains("RestoreCfg");
    assertThat(toString).contains("ignoreFilesInTarget");
    assertThat(toString).contains("test-file");
  }
}