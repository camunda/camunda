/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.unit.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.gatekeeper.spring.filter.WebappFilterChainCustomizer;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

final class WebappFilterChainCustomizerTest {

  @Test
  void shouldBeUsableAsLambda() throws Exception {
    final HttpSecurity mockHttp = mock(HttpSecurity.class);
    final WebappFilterChainCustomizer customizer = http -> {};
    customizer.customize(mockHttp);
    // No exception = success; verifies functional interface contract
  }

  @Test
  void shouldReceiveHttpSecurityInstance() throws Exception {
    final HttpSecurity mockHttp = mock(HttpSecurity.class);
    final var called = new boolean[] {false};
    final WebappFilterChainCustomizer customizer =
        http -> {
          assertThat(http).isSameAs(mockHttp);
          called[0] = true;
        };
    customizer.customize(mockHttp);
    assertThat(called[0]).isTrue();
  }
}
