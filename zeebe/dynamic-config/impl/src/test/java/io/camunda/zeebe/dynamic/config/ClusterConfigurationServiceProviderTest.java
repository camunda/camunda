/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class ClusterConfigurationServiceProviderTest {

  @Test
  void shouldDiscoverImplementationViaServiceLoader() {
    // given
    // the implementation is only registered in META-INF/services, not referenced by the api

    // when
    final var provider = ClusterConfigurationServiceProvider.load();

    // then
    assertThat(provider).isInstanceOf(DefaultClusterConfigurationServiceProvider.class);
  }
}
