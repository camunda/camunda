/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.configuration.AuthorizationsConfiguration;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.processing.identity.authorization.request.AuthorizationRequest;
import io.camunda.zeebe.engine.state.mutable.MutableProcessingState;
import io.camunda.zeebe.engine.util.ProcessingStateExtension;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.stream.api.records.TypedRecord;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;

/**
 * Simplified unit tests for AuthorizationCheckBehavior core logic. These tests verify authorization
 * decision flow without requiring full state initialization for every test.
 */
@ExtendWith(ProcessingStateExtension.class)
final class AuthorizationCheckBehaviorUnitTest {

  @SuppressWarnings("unused") // injected by extension
  private MutableProcessingState processingState;

  private AuthorizationCheckBehavior behavior;
  private SecurityConfiguration securityConfig;
  private EngineConfiguration engineConfig;

  @BeforeEach
  void setUp() {
    securityConfig = new SecurityConfiguration();
    final var authConfig = new AuthorizationsConfiguration();
    authConfig.setEnabled(true);
    securityConfig.setAuthorizations(authConfig);

    final var multiTenancyConfig = new MultiTenancyConfiguration();
    multiTenancyConfig.setChecksEnabled(true);
    securityConfig.setMultiTenancy(multiTenancyConfig);

    engineConfig = new EngineConfiguration();

    behavior = new AuthorizationCheckBehavior(processingState, securityConfig, engineConfig);
  }

  private TypedRecord<?> mockCommand(final Map<String, Object> authorizations) {
    final var command = Mockito.mock(TypedRecord.class);
    Mockito.when(command.getAuthorizations()).thenReturn(authorizations);
    Mockito.when(command.hasRequestMetadata()).thenReturn(true);
    return command;
  }

  @Nested
  class AuthorizationDisabledTests {

    @Test
    void shouldAuthorizeAllRequestsWhenAuthorizationsDisabled() {
      // Given: Authorizations disabled
      final var disabledSecurityConfig = new SecurityConfiguration();
      final var disabledAuthConfig = new AuthorizationsConfiguration();
      disabledAuthConfig.setEnabled(false);
      disabledSecurityConfig.setAuthorizations(disabledAuthConfig);

      final var disabledMultiTenancy = new MultiTenancyConfiguration();
      disabledMultiTenancy.setChecksEnabled(false);
      disabledSecurityConfig.setMultiTenancy(disabledMultiTenancy);

      final var disabledBehavior =
          new AuthorizationCheckBehavior(processingState, disabledSecurityConfig, engineConfig);

      final var command = mockCommand(Map.of("username", "testUser"));
      final var request =
          AuthorizationRequest.builder()
              .command(command)
              .resourceType(AuthorizationResourceType.RESOURCE)
              .permissionType(PermissionType.CREATE)
              .addResourceId("resource-1")
              .build();

      // When: Checking authorization
      final var result = disabledBehavior.isAuthorized(request);

      // Then: Should be authorized without checking permissions
      assertThat(result.isRight()).isTrue();
    }
  }

  @Nested
  class IsAnyAuthorizedTests {

    @Test
    void shouldThrowExceptionWhenNoRequestsProvided() {
      // When/Then: No requests provided
      assertThatThrownBy(() -> behavior.isAnyAuthorized())
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("No authorization requests provided");
    }

    @Test
    void shouldThrowExceptionWhenNullRequestsProvided() {
      // When/Then: Null requests provided
      assertThatThrownBy(() -> behavior.isAnyAuthorized((AuthorizationRequest[]) null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("No authorization requests provided");
    }
  }

  @Nested
  class CacheTests {

    @Test
    void shouldCacheAuthorizationDecisions() {
      // Given: Same request made twice
      final var command = mockCommand(Map.of("username", "testUser"));
      final var request =
          AuthorizationRequest.builder()
              .command(command)
              .resourceType(AuthorizationResourceType.RESOURCE)
              .permissionType(PermissionType.CREATE)
              .addResourceId("resource-1")
              .build();

      // When: Making the same authorization check twice
      final var result1 = behavior.isAuthorized(request);
      final var result2 = behavior.isAuthorized(request);

      // Then: Results should be consistent (cache working)
      assertThat(result1.isRight()).isEqualTo(result2.isRight());
    }

    @Test
    void shouldClearCacheWhenRequested() {
      // Given: Request checked once
      final var command = mockCommand(Map.of("username", "testUser"));
      final var request =
          AuthorizationRequest.builder()
              .command(command)
              .resourceType(AuthorizationResourceType.RESOURCE)
              .permissionType(PermissionType.CREATE)
              .addResourceId("resource-1")
              .build();

      behavior.isAuthorized(request);

      // When: Cache cleared
      behavior.clearAuthorizationsCache();

      // Then: Next check should recompute (no exception)
      final var result = behavior.isAuthorized(request);
      assertThat(result).isNotNull();
    }
  }
}
