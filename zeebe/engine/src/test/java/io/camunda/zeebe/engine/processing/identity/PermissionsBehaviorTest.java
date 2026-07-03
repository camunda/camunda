/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.security.api.model.config.AuthenticationConfiguration;
import io.camunda.security.api.model.config.initialization.InitializationConfiguration;
import io.camunda.security.configuration.EngineSecurityConfig;
import io.camunda.security.configuration.EngineSecurityConfigurations;
import io.camunda.security.core.authz.LazyTokenClaimsConverter;
import io.camunda.security.core.port.in.AuthorizationCheckPort;
import io.camunda.zeebe.engine.state.immutable.AuthorizationState;
import io.camunda.zeebe.engine.state.immutable.ProcessingState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class PermissionsBehaviorTest {

  @Mock private ProcessingState processingState;
  @Mock private AuthorizationState authorizationState;
  @Mock private AuthorizationCheckPort authCheckPort;
  @Mock private LazyTokenClaimsConverter claimsConverter;
  @Mock private AuthenticationConfiguration authConfig;
  @Mock private TypedRecord<AuthorizationRecord> command;

  @BeforeEach
  void setUp() {
    when(processingState.getAuthorizationState()).thenReturn(authorizationState);
  }

  @Test
  void shouldRejectWithForbiddenWhenNoIdentityClaimsAndAuthorizationsEnabled() {
    // given — authorizations enabled and a command carrying neither a username nor a clientId claim
    final var behavior = permissionsBehavior(/* authorizationsEnabled= */ true, false);
    when(command.isInternalCommand()).thenReturn(false);
    when(command.getAuthorizations()).thenReturn(Map.of());

    // when
    final var result = behavior.isAuthorized(command, PermissionType.CREATE);

    // then — a clean FORBIDDEN rejection (matching main) instead of a thrown exception, and the CSL
    // path is never invoked
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft().type()).isEqualTo(RejectionType.FORBIDDEN);
    assertThat(result.getLeft().reason())
        .isEqualTo(
            "Insufficient permissions to perform operation 'CREATE' on resource 'AUTHORIZATION'");
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  @Test
  void shouldAuthorizeWhenNoIdentityClaimsAndAuthorizationsDisabled() {
    // given — authorizations disabled (multi-tenancy enabled) and no username/clientId claim
    final var behavior = permissionsBehavior(/* authorizationsEnabled= */ false, true);
    final var record = new AuthorizationRecord();
    when(command.isInternalCommand()).thenReturn(false);
    when(command.getAuthorizations()).thenReturn(Map.of());
    when(command.getValue()).thenReturn(record);

    // when
    final var result = behavior.isAuthorized(command, PermissionType.CREATE);

    // then — authorized like main when authorizations are disabled, and the CSL path is never
    // invoked
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).isSameAs(record);
    verifyNoInteractions(claimsConverter, authCheckPort);
  }

  private PermissionsBehavior permissionsBehavior(
      final boolean authorizationsEnabled, final boolean multiTenancyChecksEnabled) {
    final var securityConfig =
        new EngineSecurityConfig(
            authConfig,
            authorizationsEnabled,
            multiTenancyChecksEnabled,
            new InitializationConfiguration(),
            EngineSecurityConfigurations.ID_VALIDATION_PATTERN,
            EngineSecurityConfigurations.GROUP_ID_VALIDATION_PATTERN);
    return new PermissionsBehavior(processingState, authCheckPort, claimsConverter, securityConfig);
  }
}
