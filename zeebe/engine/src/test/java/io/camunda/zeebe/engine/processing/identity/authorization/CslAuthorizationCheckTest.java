/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.context.TokenClaimsAuthenticationResolver;
import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.engine.processing.identity.AuthorizationRejectionMapper;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class CslAuthorizationCheckTest {

  @Mock private AuthorizationCheckPort authCheckPort;
  @Mock private TokenClaimsAuthenticationResolver claimsConverter;
  @Mock private AuthenticationConfiguration authConfig;
  @Mock private TypedRecord<?> command;

  @Test
  void shouldReturnNoPrincipalRejectionWhenNoIdentityClaimsAndAuthorizationsEnabled() {
    // given — authorizations enabled and a command carrying neither a username nor a clientId claim
    final var cslCheck = cslCheck(/* authorizationsEnabled= */ true, false);
    when(command.isInternalCommand()).thenReturn(false);
    when(command.getAuthorizations()).thenReturn(Map.of());

    // when
    final var result =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());

    // then — the stable no-principal rejection (a resolved principal would otherwise throw in CSL);
    // the CSL claims converter is never invoked
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.getLeft().reason())
        .isEqualTo("No authenticated user or client could be determined for the request.");
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  @Test
  void shouldSkipWhenNoIdentityClaimsAndAuthorizationsDisabled() {
    // given — authorizations disabled (multi-tenancy enabled) and no username/clientId claim
    final var cslCheck = cslCheck(/* authorizationsEnabled= */ false, true);
    when(command.isInternalCommand()).thenReturn(false);
    when(command.getAuthorizations()).thenReturn(Map.of());

    // when
    final var result =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());

    // then — skip-logic allows (empty principal); the CSL claims converter is never invoked
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEmpty();
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  @Test
  void shouldSkipForAnonymousUser() {
    // given
    final var cslCheck = cslCheck(/* authorizationsEnabled= */ true, false);
    when(command.isInternalCommand()).thenReturn(false);
    when(command.getAuthorizations())
        .thenReturn(Map.of(Authorization.AUTHORIZED_ANONYMOUS_USER, true));

    // when
    final var result =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());

    // then — anonymous access skips the check
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEmpty();
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  @Test
  void shouldSkipForInternalCommand() {
    // given
    final var cslCheck = cslCheck(/* authorizationsEnabled= */ true, false);
    when(command.isInternalCommand()).thenReturn(true);

    // when
    final var result =
        cslCheck.resolveForCheck(command, AuthorizationRejectionMapper.noPrincipal());

    // then — internal commands skip the check
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isEmpty();
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  private CslAuthorizationCheck cslCheck(
      final boolean authorizationsEnabled, final boolean multiTenancyChecksEnabled) {
    final var securityConfig =
        new EngineSecurityConfig(
            authConfig,
            authorizationsEnabled,
            multiTenancyChecksEnabled,
            new InitializationConfiguration(),
            EngineSecurityConfigurations.ID_VALIDATION_PATTERN,
            EngineSecurityConfigurations.GROUP_ID_VALIDATION_PATTERN);
    return new CslAuthorizationCheck(authCheckPort, claimsConverter, securityConfig);
  }
}
