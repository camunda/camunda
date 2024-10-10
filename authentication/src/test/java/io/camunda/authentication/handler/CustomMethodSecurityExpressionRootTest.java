/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.auth.impl.Authorization;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
public class CustomMethodSecurityExpressionRootTest {
  @MockBean private JwtAuthenticationToken authentication;
  @MockBean private AuthorizationServices<AuthorizationRecord> authorizationServices;

  @BeforeEach
  void setup() {
    when(authorizationServices.fetchAssignedPermissions(
            "1", AuthorizationResourceType.PROCESS_DEFINITION, "*"))
        .thenReturn(
            Arrays.stream(PermissionType.values()).map(Enum::name).collect(Collectors.toSet()));
    when(authorizationServices.fetchAssignedPermissions(
            "2", AuthorizationResourceType.PROCESS_DEFINITION, "*"))
        .thenReturn(Set.of(PermissionType.READ.name()));
    when(authorizationServices.fetchAssignedPermissions(
            "2", AuthorizationResourceType.PROCESS_DEFINITION, "process1"))
        .thenReturn(
            Set.of(
                PermissionType.CREATE.name(),
                PermissionType.DELETE.name(),
                PermissionType.UPDATE.name()));
    when(authorizationServices.fetchAssignedPermissions(
            "3", AuthorizationResourceType.DECISION_DEFINITION, "*"))
        .thenReturn(Set.of(PermissionType.READ.name()));
    when(authorizationServices.fetchAssignedPermissions(
            "3", AuthorizationResourceType.PROCESS_DEFINITION, "process1"))
        .thenReturn(Set.of(PermissionType.READ.name()));
  }

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
    return Stream.of(
        Arguments.of(jwtAuthentication(), AuthorizationResourceType.PROCESS_DEFINITION, "*", false),
        Arguments.of(
            basicAuthentication(1), AuthorizationResourceType.PROCESS_DEFINITION, "*", true),
        Arguments.of(
            basicAuthentication(2), AuthorizationResourceType.PROCESS_DEFINITION, "*", true),
        Arguments.of(
            basicAuthentication(2),
            AuthorizationResourceType.PROCESS_DEFINITION,
            "process1",
            false),
        Arguments.of(
            basicAuthentication(3), AuthorizationResourceType.PROCESS_DEFINITION, "*", false),
        Arguments.of(
            basicAuthentication(3),
            AuthorizationResourceType.PROCESS_DEFINITION,
            "process1",
            true));
  }

  private static Stream<Arguments> provideWriteAccessCases() {
    return Stream.of(
        Arguments.of(jwtAuthentication(), AuthorizationResourceType.PROCESS_DEFINITION, "*", false),
        Arguments.of(
            basicAuthentication(1), AuthorizationResourceType.PROCESS_DEFINITION, "*", true),
        Arguments.of(
            basicAuthentication(2), AuthorizationResourceType.PROCESS_DEFINITION, "*", false),
        Arguments.of(
            basicAuthentication(2), AuthorizationResourceType.PROCESS_DEFINITION, "process1", true),
        Arguments.of(
            basicAuthentication(3), AuthorizationResourceType.PROCESS_DEFINITION, "*", false),
        Arguments.of(
            basicAuthentication(3),
            AuthorizationResourceType.PROCESS_DEFINITION,
            "process1",
            false));
  }

  private static Authentication jwtAuthentication() {
    final Jwt jwt =
        new Jwt(
            Authorization.jwtEncoder()
                .withIssuer("issuer1")
                .withAudience("aud")
                .withSubject("sub")
                .build(),
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(100),
            Map.of("alg", "RSA256"),
            Map.of("sub", "sub1", "aud", "aud1", "groups", List.of("g1", "g2")));
    return new JwtAuthenticationToken(jwt);
  }

  private static Authentication basicAuthentication(final long userKey) {
    return new UsernamePasswordAuthenticationToken(
        CamundaUserBuilder.aCamundaUser()
            .withUsername("admin")
            .withPassword("admin")
            .withUserKey(userKey)
            .build(),
        null);
  }
}
