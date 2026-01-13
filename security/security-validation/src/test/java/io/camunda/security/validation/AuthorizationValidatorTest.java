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

import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AuthorizationValidatorTest {

  private static final AuthorizationValidator VALIDATOR =
      new AuthorizationValidator(new IdentifierValidator(Pattern.compile("^[a-zA-Z0-9_~@.+-]+$")));

  @Test
  public void shouldValidateOwnerType() {
    // when:
    final List<String> violations =
        VALIDATOR.validateIdBased(
            "foo",
            null,
            AuthorizationResourceType.RESOURCE,
            WILDCARD_CHAR,
            Set.of(PermissionType.READ));

    // then:
    assertThat(violations).hasSize(1);
    assertThat(violations).contains("No ownerType provided");
  }

  @Test
  public void shouldSuccessfullyValidateIdBased() {
    // when:
    final List<String> violations =
        VALIDATOR.validateIdBased(
            "foo",
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.RESOURCE,
            WILDCARD_CHAR,
            Set.of(PermissionType.READ));

    // then:
    assertThat(violations).isEmpty();
  }

  @Test
  void shouldNotAllowWildcardAsPropertyName() {
    // when:
    final List<String> violations =
        VALIDATOR.validatePropertyBased(
            "foo",
            AuthorizationOwnerType.USER,
            AuthorizationResourceType.RESOURCE,
            WILDCARD_CHAR,
            Set.of(PermissionType.READ));

    // then:
    assertThat(violations).hasSize(1);
    assertThat(violations)
        .anyMatch(
            s -> s.contains("The provided resourcePropertyName contains illegal characters."));
  }
}
