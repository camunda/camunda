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

import io.camunda.zeebe.gateway.rest.config.GatewayRestConfiguration.ProcessInstanceExportConfiguration;
import org.junit.jupiter.api.Test;

class ProcessInstanceExportConfigurationTest {

  @Test
  void shouldRejectNonPositiveMaxRows() {
    final var cfg = new ProcessInstanceExportConfiguration();
    assertThatThrownBy(() -> cfg.setMaxRows(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cfg.setMaxRows(-1)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldAcceptArbitrarilyLargeMaxRows() {
    final var cfg = new ProcessInstanceExportConfiguration();
    cfg.setMaxRows(1_000_000);
    assertThat(cfg.getMaxRows()).isEqualTo(1_000_000);
  }

  @Test
  void shouldRejectNonPositivePageSize() {
    final var cfg = new ProcessInstanceExportConfiguration();
    assertThatThrownBy(() -> cfg.setPageSize(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> cfg.setPageSize(-5)).isInstanceOf(IllegalArgumentException.class);
  }
}
