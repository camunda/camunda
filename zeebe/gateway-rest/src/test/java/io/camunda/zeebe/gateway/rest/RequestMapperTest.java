/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest;

import static io.camunda.zeebe.auth.Authorization.USER_TOKEN_CLAIM_PREFIX;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import io.camunda.authentication.CamundaJwtAuthenticationToken;
import io.camunda.authentication.entity.AuthenticationContext;
import io.camunda.authentication.entity.CamundaJwtUser;
import io.camunda.authentication.entity.CamundaOidcUser;
import io.camunda.authentication.entity.CamundaUser.CamundaUserBuilder;
import io.camunda.authentication.entity.OAuthContext;
import io.camunda.service.ProcessInstanceServices.ProcessInstanceMigrationBatchOperationRequest;
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.zeebe.auth.Authorization;
import io.camunda.zeebe.gateway.protocol.rest.MigrateProcessInstanceMappingInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceFilter;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationBatchOperationInstruction;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceMigrationInstruction;
import io.camunda.zeebe.util.Either;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

class RequestMapperTest {

  @Mock private RequestAttributes requestAttributes;

  @BeforeEach
  void setup() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    RequestContextHolder.setRequestAttributes(requestAttributes);
  }

  @Test
  void tokenContainsUsernameWithBasicAuth() {

    // given
    final var username = "username";
    setUsernamePasswordAuthenticationInContext(username);

    // when
    final var claims = RequestMapper.getAuthentication().claims();

    // then
    assertNotNull(claims);
    assertThat(claims).containsKey(Authorization.AUTHORIZED_USERNAME);
    assertThat(claims.get(Authorization.AUTHORIZED_USERNAME)).isEqualTo(username);
  }

  @Test
  void tokenContainsExtraClaimsWithOidcAuth() {
    // given
    final String usernameClaim = "sub";
    final String usernameValue = "test-user";
    setOidcAuthenticationInContext(usernameClaim, usernameValue, "aud1");

    // when
    final var authenticatedUsername = RequestMapper.getAuthentication().authenticatedUsername();

    // then
    assertThat(authenticatedUsername).isEqualTo(usernameValue);
  }

  @Test
  void tokenContainsExtraClaimsWithJwtAuth() {

    // given
    final String sub1 = "sub1";
    final String aud1 = "aud1";
    setJwtAuthenticationInContext(sub1, aud1);

    // when
    final var claims = RequestMapper.getAuthentication().claims();

    // then
    assertNotNull(claims);
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "sub");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "aud");
    assertThat(claims).containsKey(USER_TOKEN_CLAIM_PREFIX + "groups");
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "sub")).isEqualTo(sub1);
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "aud")).isEqualTo(aud1);
    assertThat(claims.get(USER_TOKEN_CLAIM_PREFIX + "groups")).isEqualTo(List.of("g1", "g2"));
  }

  @Test
  void tokenContainsTenantIdsInAuthenticationContext() {
    // given
    final var username = "test-user";
    final var tenants =
        List.of(
            new TenantDTO(1L, "tenant-1", "Tenant One", "First"),
            new TenantDTO(2L, "tenant-2", "Tenant Two", "Second"));
    final var authenticationContext =
        new AuthenticationContext(username, List.of(), List.of(), tenants, List.of());

    final var principal =
        new CamundaOidcUser(
            new DefaultOidcUser(
                Collections.emptyList(),
                new OidcIdToken(
                    "tokenValue",
                    Instant.now(),
                    Instant.now().plusSeconds(3600),
                    Map.of("sub", username))),
            Collections.emptySet(),
            Collections.emptySet(),
            authenticationContext);

    final var auth = new OAuth2AuthenticationToken(principal, List.of(), "oidc");
    SecurityContextHolder.getContext().setAuthentication(auth);

    // when
    final var authContext = RequestMapper.getAuthentication();

    // then
    assertThat(authContext.authenticatedTenantIds())
        .containsExactlyInAnyOrder("tenant-1", "tenant-2");
    assertThat(authContext.authenticatedUsername()).isEqualTo(username);
  }

  @Test
  void shouldMapToProcessInstanceMigrationBatchOperationRequest() {
    // given
    final var migrationInstruction = new ProcessInstanceMigrationInstruction();
    migrationInstruction.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId("source1");
    mappingInstruction.setTargetElementId("target1");
    migrationInstruction.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationInstruction = new ProcessInstanceMigrationBatchOperationInstruction();
    batchOperationInstruction.setMigrationPlan(migrationInstruction);
    final var filter = new ProcessInstanceFilter();
    batchOperationInstruction.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceMigrationBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationInstruction);

    // then
    assertTrue(result.isRight());
    final var request = result.get();
    assertThat(request.targetProcessDefinitionKey()).isEqualTo(123L);
    assertThat(request.mappingInstructions())
        .hasSize(1)
        .first()
        .satisfies(
            instruction -> {
              assertThat(instruction.getSourceElementId()).isEqualTo("source1");
              assertThat(instruction.getTargetElementId()).isEqualTo("target1");
            });
  }

  @Test
  void shouldReturnProblemDetailForInvalidInput() {
    // given
    final var migrationInstruction = new ProcessInstanceMigrationInstruction();
    migrationInstruction.setTargetProcessDefinitionKey("123");
    final var mappingInstruction = new MigrateProcessInstanceMappingInstruction();
    mappingInstruction.setSourceElementId(null);
    mappingInstruction.setTargetElementId(null);
    migrationInstruction.setMappingInstructions(List.of(mappingInstruction));

    final var batchOperationInstruction = new ProcessInstanceMigrationBatchOperationInstruction();
    batchOperationInstruction.setMigrationPlan(migrationInstruction);
    final var filter = new ProcessInstanceFilter();
    batchOperationInstruction.setFilter(filter);

    // when
    final Either<ProblemDetail, ProcessInstanceMigrationBatchOperationRequest> result =
        RequestMapper.toProcessInstanceMigrationBatchOperationRequest(batchOperationInstruction);

    // then
    assertTrue(result.isLeft());
    final var problemDetail = result.getLeft();
    assertThat(problemDetail.getStatus()).isEqualTo(400); // Bad Request
    assertThat(problemDetail.getDetail()).contains("are required");
  }

  private void setJwtAuthenticationInContext(final String sub, final String aud) {
    final Jwt jwt =
        new Jwt(
            JWT.create()
                .withIssuer("issuer1")
                .withAudience(aud)
                .withSubject(sub)
                .sign(Algorithm.none()),
            Instant.ofEpochSecond(10),
            Instant.ofEpochSecond(100),
            Map.of("alg", "RSA256"),
            Map.of("sub", sub, "aud", aud, "groups", List.of("g1", "g2")));

    final AbstractAuthenticationToken token =
        new CamundaJwtAuthenticationToken(
            jwt,
            new CamundaJwtUser(
                jwt,
                new OAuthContext(
                    new HashSet<>(),
                    new HashSet<>(),
                    new AuthenticationContext(
                        sub, List.of(), List.of(), List.of(), List.of("g1", "g2")))),
            null,
            null);
    SecurityContextHolder.getContext().setAuthentication(token);
  }

  private void setOidcAuthenticationInContext(
      final String usernameClaim, final String usernameValue, final String aud) {
    final String tokenValue = "{}";
    final Instant tokenIssuedAt = Instant.now().truncatedTo(ChronoUnit.SECONDS);
    final Instant tokenExpiresAt = tokenIssuedAt.plus(1, ChronoUnit.DAYS);

    final var oauth2Authentication =
        new OAuth2AuthenticationToken(
            new CamundaOidcUser(
                new DefaultOidcUser(
                    Collections.emptyList(),
                    new OidcIdToken(
                        tokenValue,
                        tokenIssuedAt,
                        tokenExpiresAt,
                        Map.of("aud", aud, usernameClaim, usernameValue))),
                Collections.emptySet(),
                Collections.emptySet(),
                new AuthenticationContext(
                    usernameValue, List.of(), List.of(), List.of(), List.of())),
            List.of(),
            "oidc");

    SecurityContextHolder.getContext().setAuthentication(oauth2Authentication);
  }

  private void setUsernamePasswordAuthenticationInContext(final String username) {
    final UsernamePasswordAuthenticationToken authenticationToken =
        new UsernamePasswordAuthenticationToken(
            CamundaUserBuilder.aCamundaUser()
                .withUsername(username)
                .withPassword("admin")
                .withUserKey(1L)
                .build(),
            null);
    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
  }
}
