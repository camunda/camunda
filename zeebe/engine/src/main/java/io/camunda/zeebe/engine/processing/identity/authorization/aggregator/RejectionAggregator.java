/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.identity.authorization.aggregator;

import io.camunda.zeebe.engine.processing.Rejection;
import io.camunda.zeebe.engine.processing.identity.authorization.result.AuthorizationRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/** Aggregates multiple {@link AuthorizationRejection} instances into a single {@link Rejection}. */
public final class RejectionAggregator {

  private RejectionAggregator() {
    // utility class
  }

  /**
   * Aggregates a list of authorization rejections into a single rejection. Prioritizes permission
   * rejections first, then tenant rejections.
   *
   * @param rejections the list of collected authorization rejections
   * @return a combined {@link Rejection}
   */
  public static Rejection aggregate(final List<AuthorizationRejection> rejections) {
    if (rejections.isEmpty()) {
      throw new IllegalArgumentException("Cannot aggregate empty list of authorization rejections");
    }

    return aggregateMatchingRejections(rejections, AuthorizationRejection::isPermission)
        .or(() -> aggregateMatchingRejections(rejections, AuthorizationRejection::isTenant))
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Authorization rejections must be either permission or tenant type, but found neither in: "
                        + rejections));
  }

  private static Optional<Rejection> aggregateMatchingRejections(
      final List<AuthorizationRejection> authorizationRejections,
      final Predicate<AuthorizationRejection> matchPredicate) {

    final var rejections =
        authorizationRejections.stream()
            .filter(matchPredicate)
            .map(AuthorizationRejection::rejection)
            .toList();

    if (rejections.isEmpty()) {
      return Optional.empty();
    }

    final var reason =
        rejections.stream().map(Rejection::reason).distinct().collect(Collectors.joining("; "));

    return Optional.of(new Rejection(rejections.getFirst().type(), reason));
  }

  /**
   * Aggregates rejections from multiple authorization paths (ANY/OR logic). All paths failed, so
   * combines messages to indicate what was tried.
   *
   * @param rejections the list of collected authorization rejections
   * @return a combined rejection with messages from all failed paths
   */
  public static Rejection aggregateComposite(final List<Rejection> rejections) {
    if (rejections.isEmpty()) {
      throw new IllegalArgumentException("Cannot aggregate empty list of rejections");
    }

    // Combine all rejection messages
    final var reason =
        rejections.stream().map(Rejection::reason).distinct().collect(Collectors.joining("; and "));

    // Use FORBIDDEN if any rejection is FORBIDDEN, otherwise use first rejection type
    final var hasForbidden = rejections.stream().anyMatch(r -> r.type() == RejectionType.FORBIDDEN);
    final var type = hasForbidden ? RejectionType.FORBIDDEN : rejections.getFirst().type();

    return new Rejection(type, reason);
  }
}
