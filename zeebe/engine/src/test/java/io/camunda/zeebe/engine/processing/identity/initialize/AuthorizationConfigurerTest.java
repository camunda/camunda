/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static io.camunda.zeebe.protocol.record.value.AuthorizationScope.WILDCARD_CHAR;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredAuthorization;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class AuthorizationConfigurerTest {

  public static final ConfiguredAuthorization INVALID_OWNER_TYPE =
      new ConfiguredAuthorization(
          AuthorizationOwnerType.UNSPECIFIED,
          "foo",
          AuthorizationResourceType.RESOURCE,
          WILDCARD_CHAR,
          Set.of(PermissionType.READ));
  public static final ConfiguredAuthorization VALID_AUTH =
      new ConfiguredAuthorization(
          AuthorizationOwnerType.USER,
          "foo",
          AuthorizationResourceType.RESOURCE,
          WILDCARD_CHAR,
          Set.of(PermissionType.READ));

  @Test
  public void shouldValidateOwnerType() {
    // when:
    final Either<List<String>, AuthorizationRecord> result =
        new AuthorizationConfigurer(Pattern.compile(".*")).configure(INVALID_OWNER_TYPE);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("Authorization owner must not be UNSPECIFIED");
  }

  @Test
  public void shouldSuccessfullyConfigure() {
    // when:
    final Either<List<String>, AuthorizationRecord> result =
        new AuthorizationConfigurer(Pattern.compile(".*")).configure(VALID_AUTH);

    // then:
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAggregateToViolations() {
    // given:
    final List<ConfiguredAuthorization> auths =
        List.of(VALID_AUTH, INVALID_OWNER_TYPE, INVALID_OWNER_TYPE);

    // when:
    final Either<List<String>, List<AuthorizationRecord>> result =
        new AuthorizationConfigurer(Pattern.compile(".*")).configureEntities(auths);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("Authorization owner must not be UNSPECIFIED");
    assertThat(result.getLeft()).hasSize(2);
  }

  @Test
  void shouldAggregateToAuthorizationRecords() {
    // given:
    final List<ConfiguredAuthorization> auths = List.of(VALID_AUTH, VALID_AUTH);

    // when:
    final Either<List<String>, List<AuthorizationRecord>> result =
        new AuthorizationConfigurer(Pattern.compile(".*")).configureEntities(auths);

    // then:
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).hasSize(2);
  }
}
