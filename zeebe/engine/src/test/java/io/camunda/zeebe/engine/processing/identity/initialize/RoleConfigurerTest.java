/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredRole;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.security.validation.RoleValidator;
import io.camunda.zeebe.protocol.impl.record.value.authorization.RoleRecord;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class RoleConfigurerTest {

  private static final ConfiguredRole MISSING_ROLE_ID =
      new ConfiguredRole(
          null,
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());
  private static final ConfiguredRole VALID_ROLE =
      new ConfiguredRole(
          "foo",
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());

  private static final RoleValidator VALIDATOR =
      new RoleValidator(new IdentifierValidator(Pattern.compile(".*"), Pattern.compile(".*")));
  private static final RoleConfigurer CONFIGURER = new RoleConfigurer(VALIDATOR);

  @Test
  void shouldReturnViolationOnValidationFailure() {
    // when:
    final Either<List<String>, RoleRecord> result = CONFIGURER.configure(MISSING_ROLE_ID);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No roleId provided");
  }

  @Test
  void shouldSuccessfullyConfigure() {
    // when:
    final Either<List<String>, RoleRecord> result = CONFIGURER.configure(VALID_ROLE);

    // then:
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAggregateToViolations() {
    // given:
    final List<ConfiguredRole> roles = List.of(VALID_ROLE, MISSING_ROLE_ID, MISSING_ROLE_ID);

    // when:
    final Either<List<String>, List<RoleRecord>> result = CONFIGURER.configureEntities(roles);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No roleId provided");
    assertThat(result.getLeft()).hasSize(2);
  }

  @Test
  void shouldAggregateToTenantRecords() {
    // given:
    final List<ConfiguredRole> tenants = List.of(VALID_ROLE, VALID_ROLE);

    // when:
    final Either<List<String>, List<RoleRecord>> result = CONFIGURER.configureEntities(tenants);

    // then:
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).hasSize(2);
  }
}
