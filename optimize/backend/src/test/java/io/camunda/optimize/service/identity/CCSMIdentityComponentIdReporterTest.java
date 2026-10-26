/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.IdentityConfiguration;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.event.Level;

class CCSMIdentityComponentIdReporterTest {

  @RegisterExtension
  private final LogCapturer logs =
      LogCapturer.create().captureForType(CCSMIdentityComponentIdReporter.class, Level.INFO);

  private static void reportWith(final String componentId) {
    final IdentityConfiguration configuration = mock(IdentityConfiguration.class);
    when(configuration.getComponentId()).thenReturn(componentId);

    new CCSMIdentityComponentIdReporter(configuration).report();
  }

  @Test
  void saysWhichComponentItNamesItselfAs() {
    reportWith("optimize");

    logs.assertContains("Naming this component 'optimize'");
    assertThat(logs.getEvents()).singleElement().returns(Level.INFO, event -> event.getLevel());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void warnsWhenItHasNoneToName(final String componentId) {
    // The failure this exists for: Identity falls back to returning everything, and nothing errors.
    reportWith(componentId);

    logs.assertContains("every permission on Optimize's audience");
    // At WARN specifically, since the point is that this is visible rather than merely recorded.
    assertThat(logs.getEvents()).singleElement().returns(Level.WARN, event -> event.getLevel());
  }
}
