/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD_CHAR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AuthorizationValidatorTest {

  private static final AuthorizationValidator VALIDATOR =
      new AuthorizationValidator(
          new IdentifierValidator(
              Pattern.compile("^[a-zA-Z0-9_~@.+-]+$"), Pattern.compile("^[a-zA-Z0-9_~@.+-]+$")));

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("validAuthorizationCases")
  void shouldSuccessfullyValidate(final TestAuthorization authorizationTestCase) {
    // when
    final var violations = validateAuthorization(authorizationTestCase);

    // then
    assertThat(violations).isEmpty();
  }

  @ParameterizedTest(name = "{index}: {0}")
  @MethodSource("invalidAuthorizationCases")
  void shouldFailValidation(
      final TestAuthorization authorizationTestCase, final String expectedViolation) {
    // when
    final var violations = validateAuthorization(authorizationTestCase);

    // then
    assertThat(violations).containsExactly(expectedViolation);
  }

  static Stream<Arguments> validAuthorizationCases() {
    final var permissions = Set.of(PermissionType.READ);
    return Stream.of(
        arguments(
            named(
                "ID-based with wildcard",
                new IdBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    WILDCARD_CHAR,
                    permissions))),
        arguments(
            named(
                "ID-based with specific resource",
                new IdBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "resource-123",
                    permissions))),
        arguments(
            named(
                "Property-based with valid property name",
                new PropertyBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "propertyName",
                    permissions))));
  }

  static Stream<Arguments> invalidAuthorizationCases() {
    final var permissions = Set.of(PermissionType.READ);
    return Stream.of(
        // Missing ownerId cases
        arguments(
            named(
                "ID-based without ownerId",
                new IdBasedAuthorization(
                    null,
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "1",
                    permissions)),
            "No ownerId provided"),
        arguments(
            named(
                "Property-based without ownerId",
                new PropertyBasedAuthorization(
                    "",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "propertyName",
                    permissions)),
            "No ownerId provided"),
        // Missing ownerType cases
        arguments(
            named(
                "ID-based without ownerType",
                new IdBasedAuthorization(
                    "foo", null, AuthorizationResourceType.RESOURCE, "2", permissions)),
            "No ownerType provided"),
        arguments(
            named(
                "Property-based without ownerType",
                new PropertyBasedAuthorization(
                    "foo", null, AuthorizationResourceType.RESOURCE, "propertyName", permissions)),
            "No ownerType provided"),
        // Missing resourceType cases
        arguments(
            named(
                "ID-based without resourceType",
                new IdBasedAuthorization(
                    "foo", AuthorizationOwnerType.USER, null, "3", permissions)),
            "No resourceType provided"),
        arguments(
            named(
                "Property-based without resourceType",
                new PropertyBasedAuthorization(
                    "foo", AuthorizationOwnerType.USER, null, "propertyName", permissions)),
            "No resourceType provided"),
        // Missing permissions cases
        arguments(
            named(
                "ID-based without permissions",
                new IdBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "4",
                    Set.of())),
            "No permissionTypes provided"),
        arguments(
            named(
                "Property-based without permissions",
                new PropertyBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "propertyName",
                    null)),
            "No permissionTypes provided"),
        // Missing resourceId/resourcePropertyName cases
        arguments(
            named(
                "ID-based without resourceId",
                new IdBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "",
                    permissions)),
            "No resourceId provided"),
        arguments(
            named(
                "Property-based without resourcePropertyName",
                new PropertyBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    null,
                    permissions)),
            "No resourcePropertyName provided"),
        // Invalid resourceId/resourcePropertyName cases
        arguments(
            named(
                "ID-based with invalid characters",
                new IdBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "invalid$id",
                    permissions)),
            "The provided resourceId contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'"),
        arguments(
            named(
                "Property-based with invalid characters",
                new PropertyBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    "invalid$property",
                    permissions)),
            "The provided resourcePropertyName contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'"),
        arguments(
            named(
                "Property-based with wildcard as property name",
                new PropertyBasedAuthorization(
                    "foo",
                    AuthorizationOwnerType.USER,
                    AuthorizationResourceType.RESOURCE,
                    WILDCARD_CHAR,
                    permissions)),
            "The provided resourcePropertyName contains illegal characters. It must match the pattern '^[a-zA-Z0-9_~@.+-]+$'"));
  }

  private static List<String> validateAuthorization(final TestAuthorization authorization) {
    return switch (authorization) {
      case IdBasedAuthorization idBased ->
          VALIDATOR.validateIdBased(
              idBased.ownerId(),
              idBased.ownerType(),
              idBased.resourceType(),
              idBased.resourceId(),
              idBased.permissions());
      case PropertyBasedAuthorization propertyBased ->
          VALIDATOR.validatePropertyBased(
              propertyBased.ownerId(),
              propertyBased.ownerType(),
              propertyBased.resourceType(),
              propertyBased.resourcePropertyName(),
              propertyBased.permissions());
    };
  }

  private sealed interface TestAuthorization
      permits IdBasedAuthorization, PropertyBasedAuthorization {}

  private record IdBasedAuthorization(
      String ownerId,
      AuthorizationOwnerType ownerType,
      AuthorizationResourceType resourceType,
      String resourceId,
      Set<PermissionType> permissions)
      implements TestAuthorization {}

  private record PropertyBasedAuthorization(
      String ownerId,
      AuthorizationOwnerType ownerType,
      AuthorizationResourceType resourceType,
      String resourcePropertyName,
      Set<PermissionType> permissions)
      implements TestAuthorization {}
}
