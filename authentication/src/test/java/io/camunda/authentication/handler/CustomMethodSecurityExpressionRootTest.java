/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class CustomMethodSecurityExpressionRootTest {
  @MockBean private JwtAuthenticationToken authentication;
  @MockBean private AuthorizationServices<AuthorizationRecord> authorizationServices;

  @ParameterizedTest
  @MethodSource("provideReadAccessCases")
  void hasReadAccess(
      final Authentication authentication,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final boolean hasReadAccess) {
    final CustomMethodSecurityExpressionRoot customMethodSecurityExpressionRoot =
        new CustomMethodSecurityExpressionRoot(authentication, authorizationServices);
    final boolean hasAccess =
        customMethodSecurityExpressionRoot.hasReadAccess(resourceType.name(), resourceId);

    assertThat(hasAccess).as("user has read access").isEqualTo(hasReadAccess);
  }

  @ParameterizedTest
  @MethodSource("provideWriteAccessCases")
  void hasWriteAccess(
      final Authentication authentication,
      final AuthorizationResourceType resourceType,
      final String resourceId,
      final boolean hasWriteAccess) {
    final CustomMethodSecurityExpressionRoot customMethodSecurityExpressionRoot =
        new CustomMethodSecurityExpressionRoot(authentication, authorizationServices);
    final boolean hasAccess =
        customMethodSecurityExpressionRoot.hasWriteAccess(resourceType.name(), resourceId);
    assertThat(hasAccess).as("user has write access").isEqualTo(hasWriteAccess);
  }

  private static Stream<Arguments> provideReadAccessCases() {
    return Stream.of(Arguments.of());
  }

  private static Stream<Arguments> provideWriteAccessCases() {
    return Stream.of();
  }
}
