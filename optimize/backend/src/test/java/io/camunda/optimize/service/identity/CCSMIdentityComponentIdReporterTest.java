/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.identity.sdk.IdentityConfiguration.Type;
import io.github.netmikey.logunit.api.LogCapturer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.event.Level;

@ExtendWith(MockitoExtension.class)
class CCSMIdentityComponentIdReporterTest {

  @RegisterExtension
  private final LogCapturer logs =
      LogCapturer.create().captureForType(CCSMIdentityComponentIdReporter.class, Level.INFO);

  @Mock private IdentityConfiguration identityConfiguration;

  @InjectMocks private CCSMIdentityComponentIdReporter reporter;

  @Test
  void saysWhichComponentItNamesItselfAs() {
    when(identityConfiguration.getType()).thenReturn(Type.GENERIC);
    when(identityConfiguration.getComponentId()).thenReturn("optimize");

    reporter.report();

    logs.assertContains("Naming this component 'optimize'");
    assertThat(logs.getEvents()).singleElement().returns(Level.INFO, event -> event.getLevel());
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void warnsWhenItHasNoneToName(final String componentId) {
    // The failure this exists for: Identity falls back to returning everything, and nothing errors.
    when(identityConfiguration.getType()).thenReturn(Type.GENERIC);
    when(identityConfiguration.getComponentId()).thenReturn(componentId);

    reporter.report();

    logs.assertContains("every permission on Optimize's audience");
    // At WARN specifically, since the point is that this is visible rather than merely recorded.
    assertThat(logs.getEvents()).singleElement().returns(Level.WARN, event -> event.getLevel());
  }

  @ParameterizedTest
  @EnumSource(
      value = Type.class,
      names = {"KEYCLOAK", "AUTH0"})
  void saysComponentScopingDoesNotApplyWherePermissionsComeFromTheToken(final Type type) {
    when(identityConfiguration.getType()).thenReturn(type);

    reporter.report();

    logs.assertContains("component scoping does not apply");
    assertThat(logs.getEvents()).singleElement().returns(Level.INFO, event -> event.getLevel());
    // Pins the claim: there is no component to read, so it must not even be consulted.
    verify(identityConfiguration, never()).getComponentId();
  }
}
