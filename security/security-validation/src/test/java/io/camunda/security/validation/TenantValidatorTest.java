/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class TenantValidatorTest {

  private static final TenantValidator VALIDATOR =
      new TenantValidator(new IdentifierValidator(java.util.regex.Pattern.compile(".*")));

  @Test
  public void shouldValidateMandatoryFields() {
    // when:
    final List<String> violations = VALIDATOR.validateCreate(null, "");

    // then:
    assertThat(violations).containsExactlyInAnyOrder("No tenantId provided", "No name provided");
  }

  @Test
  public void shouldSuccessfullyConfigure() {
    // when:
    final List<String> violations = VALIDATOR.validateCreate("foo", "Foo");

    // then:
    assertThat(violations).isEmpty();
  }
}
