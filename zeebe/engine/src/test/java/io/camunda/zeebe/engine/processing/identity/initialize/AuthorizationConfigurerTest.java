/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.security.validation.AuthorizationValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationConfigurerTest {

  private static final ConfiguredAuthorization INVALID_WILDCARD_AUTH_MISSING_OWNER_TYPE =
      ConfiguredAuthorization.wildcard(
          null, "foo", AuthorizationResourceType.RESOURCE, Set.of(PermissionType.READ));
  private static final ConfiguredAuthorization VALID_WILDCARD_AUTH =
      ConfiguredAuthorization.wildcard(
          AuthorizationOwnerType.USER,
          "foo",
          AuthorizationResourceType.RESOURCE,
          Set.of(PermissionType.READ));
  private static final ConfiguredAuthorization VALID_ID_BASED_AUTH =
      ConfiguredAuthorization.idBased(
          AuthorizationOwnerType.USER,
          "bar",
          AuthorizationResourceType.RESOURCE,
          "123",
          Set.of(PermissionType.READ));
  private static final ConfiguredAuthorization VALID_PROPERTY_BASED_AUTH =
      ConfiguredAuthorization.propertyBased(
          AuthorizationOwnerType.USER,
          "baz",
          AuthorizationResourceType.USER,
          "propertyName",
          Set.of(PermissionType.READ));
  private static final AuthorizationValidator VALIDATOR =
      new AuthorizationValidator(
          new IdentifierValidator(Pattern.compile(".*"), Pattern.compile(".*")));

  @Test
  void shouldReturnViolationOnValidationFailure() {
    // when:
    final Either<List<String>, AuthorizationRecord> result =
        new AuthorizationConfigurer(VALIDATOR).configure(INVALID_WILDCARD_AUTH_MISSING_OWNER_TYPE);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).containsExactly("No ownerType provided");
  }

  @Test
  void shouldReturnViolationWhenBothResourceIdAndPropertyNameProvided() {
    // given: an authorization with both resourceId and resourcePropertyName
    final var authWithBoth =
        new ConfiguredAuthorization(
            AuthorizationOwnerType.USER,
            "foo",
            AuthorizationResourceType.RESOURCE,
            "resource-123",
            "propertyName",
            Set.of(PermissionType.READ));

    // when:
    final var result = new AuthorizationConfigurer(VALIDATOR).configure(authWithBoth);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft())
        .containsExactly(
            "resourceId and resourcePropertyName are mutually exclusive. Provide only one of them.");
  }

  @ParameterizedTest
  @MethodSource("missingResourceIdentifierCases")
  void shouldReturnViolationWhenBothResourceIdAndPropertyNameMissing(
      final String missingResourceId, final String missingPropertyName) {
    // given: an authorization with neither resourceId nor resourcePropertyName
    final var authWithNeither =
        new ConfiguredAuthorization(
            AuthorizationOwnerType.USER,
            "foo",
            AuthorizationResourceType.RESOURCE,
            missingResourceId,
            missingPropertyName,
            Set.of(PermissionType.READ));

    // when:
    final var result = new AuthorizationConfigurer(VALIDATOR).configure(authWithNeither);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft())
        .containsExactly("Either resourceId or resourcePropertyName must be provided.");
  }

  private static Stream<Arguments> missingResourceIdentifierCases() {
    return Stream.of(
        argumentSet("Both null", null, null),
        argumentSet("Both empty strings", "", ""),
        argumentSet("Both blank strings", "  ", "  "),
        argumentSet("Null resourceId and empty propertyName", null, ""));
  }

  @ParameterizedTest(name = "[{index}]: {0}")
  @MethodSource("validAuthorizations")
  void shouldSuccessfullyConfigure(final ConfiguredAuthorization authorization) {
    // when:
    final Either<List<String>, AuthorizationRecord> result =
        new AuthorizationConfigurer(VALIDATOR).configure(authorization);

    // then:
    assertThat(result.isRight()).isTrue();
  }

  private static Stream<Arguments> validAuthorizations() {
    return Stream.of(
        arguments(named("Valid wildcard authorization", VALID_WILDCARD_AUTH)),
        arguments(named("Valid ID-based authorization", VALID_ID_BASED_AUTH)),
        arguments(named("Valid PROPERTY-based authorization", VALID_PROPERTY_BASED_AUTH)));
  }

  @Test
  void shouldAggregateToViolations() {
    // given:
    final List<ConfiguredAuthorization> auths =
        List.of(
            VALID_WILDCARD_AUTH,
            INVALID_WILDCARD_AUTH_MISSING_OWNER_TYPE,
            INVALID_WILDCARD_AUTH_MISSING_OWNER_TYPE);

    // when:
    final Either<List<String>, List<AuthorizationRecord>> result =
        new AuthorizationConfigurer(VALIDATOR).configureEntities(auths);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No ownerType provided");
    assertThat(result.getLeft()).hasSize(2);
  }

  @Test
  void shouldAggregateToAuthorizationRecords() {
    // given:
    final List<ConfiguredAuthorization> auths =
        List.of(VALID_WILDCARD_AUTH, VALID_ID_BASED_AUTH, VALID_PROPERTY_BASED_AUTH);

    // when:
    final Either<List<String>, List<AuthorizationRecord>> result =
        new AuthorizationConfigurer(VALIDATOR).configureEntities(auths);

    // then:
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).hasSize(3);
  }
}
