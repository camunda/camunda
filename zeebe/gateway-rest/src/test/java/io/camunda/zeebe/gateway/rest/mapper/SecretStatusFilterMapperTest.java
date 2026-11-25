/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import static io.camunda.gateway.protocol.model.SecretStatusEnum.ACTIVE;
import static io.camunda.gateway.protocol.model.SecretStatusEnum.DISABLED;
import static io.camunda.gateway.protocol.model.SecretStatusEnum.EXPIRED;
import static io.camunda.gateway.protocol.model.SecretStatusEnum.NOT_YET_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter;
import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter.Builder.IBuild;
import io.camunda.gateway.protocol.model.SecretListFilter;
import io.camunda.gateway.protocol.model.SecretListRequest;
import io.camunda.gateway.protocol.model.SecretStatusEnum;
import io.camunda.gateway.protocol.model.SecretStatusFilterProperty;
import io.camunda.secretstore.SecretStatus;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class SecretStatusFilterMapperTest {

  private static final Set<SecretStatus> NONE = EnumSet.noneOf(SecretStatus.class);

  @Test
  void shouldNotFilterWithoutRequest() {
    // when / then
    assertThat(SecretStatusFilterMapper.toSecretStatuses(null)).isNull();
  }

  @Test
  void shouldNotFilterWithoutFilter() {
    // given
    final var request = SecretListRequest.Builder.create().build();

    // when / then
    assertThat(SecretStatusFilterMapper.toSecretStatuses(request)).isNull();
  }

  @Test
  void shouldNotFilterWithoutStatus() {
    // given
    final var request =
        SecretListRequest.Builder.create()
            .filter(SecretListFilter.Builder.create().build())
            .build();

    // when / then
    assertThat(SecretStatusFilterMapper.toSecretStatuses(request)).isNull();
  }

  @ParameterizedTest
  @EnumSource(SecretStatusEnum.class)
  void shouldMapEveryApiStatusToTheStoreStatusOfTheSameName(final SecretStatusEnum status) {
    // when
    final var statuses = toSecretStatuses(filter().$eq(status));

    // then
    assertThat(statuses).containsExactly(SecretStatus.valueOf(status.name()));
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("statusFilters")
  void shouldResolveStatusFilterToMatchingStatuses(
      final String name, final IBuild filter, final Set<SecretStatus> expected) {
    // when
    final var statuses = toSecretStatuses(filter);

    // then every operator narrows the set, so operators given together must all match
    assertThat(statuses).isEqualTo(expected);
  }

  static Stream<Arguments> statusFilters() {
    return Stream.of(
        // single operators
        row("$eq", filter().$eq(EXPIRED), SecretStatus.EXPIRED),
        row(
            "$neq",
            filter().$neq(ACTIVE),
            SecretStatus.DISABLED,
            SecretStatus.EXPIRED,
            SecretStatus.NOT_YET_ACTIVE),
        row(
            "$in",
            filter().$in(List.of(DISABLED, EXPIRED)),
            SecretStatus.DISABLED,
            SecretStatus.EXPIRED),
        row(
            "$notIn",
            filter().$notIn(List.of(ACTIVE, DISABLED)),
            SecretStatus.EXPIRED,
            SecretStatus.NOT_YET_ACTIVE),
        row("$exists true", filter().$exists(true), SecretStatus.values()),
        // every listed secret has a status, so none lacks one
        row("$exists false", filter().$exists(false)),
        row("$like", filter().$like("*ACTIVE"), SecretStatus.ACTIVE, SecretStatus.NOT_YET_ACTIVE),
        // $like is case-sensitive, so a lower-case name matches no status
        row("$like matching none", filter().$like("active")),
        // no operator, an explicit null, or an empty list adds no constraint
        row("no operator", filter(), SecretStatus.values()),
        row("null $exists", filter().$exists(null), SecretStatus.values()),
        row("empty $in", filter().$in(List.of()), SecretStatus.values()),
        row("empty $notIn", filter().$notIn(List.of()), SecretStatus.values()),
        // combined operators
        row("duplicate $in", filter().$in(List.of(ACTIVE, ACTIVE)), SecretStatus.ACTIVE),
        row(
            "$in minus $notIn",
            filter().$in(List.of(ACTIVE, EXPIRED)).$notIn(List.of(ACTIVE)),
            SecretStatus.EXPIRED),
        row("$in and $notIn cancel", filter().$in(List.of(ACTIVE)).$notIn(List.of(ACTIVE))),
        row(
            "$in minus $neq",
            filter().$in(List.of(ACTIVE, EXPIRED)).$neq(ACTIVE),
            SecretStatus.EXPIRED),
        row("$eq and $neq cancel", filter().$eq(ACTIVE).$neq(ACTIVE)),
        row("$eq outside $in", filter().$eq(ACTIVE).$in(List.of(EXPIRED))),
        row("$exists false wins", filter().$exists(false).$in(List.of(ACTIVE))),
        row("$exists true with $eq", filter().$exists(true).$eq(ACTIVE), SecretStatus.ACTIVE),
        row(
            "$like minus $neq",
            filter().$like("*ACTIVE").$neq(ACTIVE),
            SecretStatus.NOT_YET_ACTIVE),
        row(
            "$like minus $notIn",
            filter().$like("*ACTIVE").$notIn(List.of(ACTIVE, NOT_YET_ACTIVE))),
        row(
            "every operator",
            filter()
                .$eq(NOT_YET_ACTIVE)
                .$neq(ACTIVE)
                .$exists(true)
                .$in(List.of(ACTIVE, NOT_YET_ACTIVE))
                .$notIn(List.of(EXPIRED))
                .$like("NOT*"),
            SecretStatus.NOT_YET_ACTIVE),
        // $like is bounded by the longest status name, NOT_YET_ACTIVE (14 characters)
        row("$like of 14 '?'", filter().$like("?".repeat(14)), SecretStatus.NOT_YET_ACTIVE),
        row("$like of 15 '?'", filter().$like("?".repeat(15))),
        row(
            "$like of 15 '?' with $in",
            filter().$like("?".repeat(15)).$in(List.of(NOT_YET_ACTIVE))));
  }

  @Test
  void shouldRejectStatusFilterOfUnexpectedType() {
    // given a status the registered deserializer never produces
    final var request =
        SecretListRequest.Builder.create()
            .filter(
                SecretListFilter.Builder.create()
                    .status(mock(SecretStatusFilterProperty.class))
                    .build())
            .build();

    // when / then
    assertThatThrownBy(() -> SecretStatusFilterMapper.toSecretStatuses(request))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageStartingWith("Unexpected status filter type");
  }

  private static IBuild filter() {
    return AdvancedSecretStatusFilter.Builder.create();
  }

  private static Set<SecretStatus> toSecretStatuses(final IBuild filter) {
    final var request =
        SecretListRequest.Builder.create()
            .filter(SecretListFilter.Builder.create().status(filter.build()).build())
            .build();
    return SecretStatusFilterMapper.toSecretStatuses(request);
  }

  private static Arguments row(
      final String name, final IBuild filter, final SecretStatus... expected) {
    return Arguments.of(
        name, filter, expected.length == 0 ? NONE : EnumSet.of(expected[0], expected));
  }
}
