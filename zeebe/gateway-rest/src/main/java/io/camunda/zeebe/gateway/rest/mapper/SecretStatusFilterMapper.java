/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.mapper;

import io.camunda.gateway.mapping.http.util.AdvancedSearchFilterUtil;
import io.camunda.gateway.protocol.model.AdvancedSecretStatusFilter;
import io.camunda.gateway.protocol.model.SecretListRequest;
import io.camunda.search.filter.Operation;
import io.camunda.secretstore.SecretStatus;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** Maps the status filter of a secret list request to the statuses it matches. */
@NullMarked
public final class SecretStatusFilterMapper {

  private static final int MAX_STATUS_NAME_LENGTH =
      Arrays.stream(SecretStatus.values()).mapToInt(s -> s.name().length()).max().getAsInt();

  private SecretStatusFilterMapper() {}

  /**
   * Resolves the status filter to the statuses it matches, or {@code null} for no filter. Every
   * operator narrows the set, so operators given together must all match, as in the search
   * endpoints. An empty {@code $in} or {@code $notIn} adds no constraint, since the model cannot
   * tell it apart from an absent one.
   */
  public static @Nullable Set<SecretStatus> toSecretStatuses(
      final @Nullable SecretListRequest request) {
    final var filter = request == null ? null : request.getFilter();
    final var status = filter == null ? null : filter.getStatus();
    if (status == null) {
      return null;
    }
    if (!(status instanceof final AdvancedSecretStatusFilter advanced)) {
      // the registered deserializer turns a plain value into {"$eq": value}
      throw new IllegalStateException(
          "Unexpected status filter type: " + status.getClass().getName());
    }
    final var statuses = EnumSet.allOf(SecretStatus.class);
    AdvancedSearchFilterUtil.mapToStringOperations()
        .apply(advanced)
        .forEach(operation -> apply(operation, statuses));
    return statuses;
  }

  private static void apply(final Operation<String> operation, final Set<SecretStatus> statuses) {
    switch (operation.operator()) {
      case EQUALS, IN -> statuses.retainAll(toSecretStatusSet(operation.values()));
      case NOT_EQUALS, NOT_IN -> statuses.removeAll(toSecretStatusSet(operation.values()));
      case EXISTS -> {}
      // every listed secret has a status, so none lacks one
      case NOT_EXISTS -> statuses.clear();
      case LIKE -> retainLike(statuses, operation.value());
      default ->
          throw new IllegalStateException(
              "Unsupported status filter operator: " + operation.operator());
    }
  }

  private static void retainLike(final Set<SecretStatus> statuses, final String like) {
    LikePattern.compile(like, MAX_STATUS_NAME_LENGTH)
        .ifPresentOrElse(
            pattern -> statuses.removeIf(candidate -> !pattern.matcher(candidate.name()).matches()),
            statuses::clear);
  }

  private static Set<SecretStatus> toSecretStatusSet(final List<String> names) {
    return names.stream()
        .map(SecretStatus::valueOf)
        .collect(Collectors.toCollection(() -> EnumSet.noneOf(SecretStatus.class)));
  }
}
