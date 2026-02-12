/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.initialize;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.security.configuration.ConfiguredGroup;
import io.camunda.security.validation.GroupValidator;
import io.camunda.security.validation.IdentifierValidator;
import io.camunda.zeebe.protocol.impl.record.value.group.GroupRecord;
import io.camunda.zeebe.util.Either;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class GroupConfigurerTest {

  private static final ConfiguredGroup MISSING_GROUP_ID =
      new ConfiguredGroup(
          null,
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());
  private static final ConfiguredGroup VALID_GROUP =
      new ConfiguredGroup(
          "foo",
          "Foo",
          "",
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList(),
          Collections.emptyList());

  private static final GroupValidator VALIDATOR =
      new GroupValidator(new IdentifierValidator(Pattern.compile(".*"), Pattern.compile(".*")));
  private static final GroupConfigurer CONFIGURER = new GroupConfigurer(VALIDATOR);

  @Test
  void shouldReturnViolationOnValidationFailure() {
    // when:
    final Either<List<String>, GroupRecord> result = CONFIGURER.configure(MISSING_GROUP_ID);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No groupId provided");
  }

  @Test
  void shouldSuccessfullyConfigure() {
    // when:
    final Either<List<String>, GroupRecord> result = CONFIGURER.configure(VALID_GROUP);

    // then:
    assertThat(result.isRight()).isTrue();
  }

  @Test
  void shouldAggregateToViolations() {
    // given:
    final List<ConfiguredGroup> groups = List.of(VALID_GROUP, MISSING_GROUP_ID, MISSING_GROUP_ID);

    // when:
    final Either<List<String>, List<GroupRecord>> result = CONFIGURER.configureEntities(groups);

    // then:
    assertThat(result.isLeft()).isTrue();
    assertThat(result.getLeft()).contains("No groupId provided");
    assertThat(result.getLeft()).hasSize(2);
  }

  @Test
  void shouldAggregateToTenantRecords() {
    // given:
    final List<ConfiguredGroup> tenants = List.of(VALID_GROUP, VALID_GROUP);

    // when:
    final Either<List<String>, List<GroupRecord>> result = CONFIGURER.configureEntities(tenants);

    // then:
    assertThat(result.isRight()).isTrue();
    assertThat(result.get()).hasSize(2);
  }
}
