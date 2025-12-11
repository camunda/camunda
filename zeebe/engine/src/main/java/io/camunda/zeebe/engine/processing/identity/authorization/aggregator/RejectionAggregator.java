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
import io.camunda.zeebe.util.Either;
import java.util.List;
import java.util.stream.Collectors;

/** Aggregates multiple {@link AuthorizationRejection} instances into a single {@link Rejection}. */
public final class RejectionAggregator {

  private RejectionAggregator() {
    // utility class
  }

  /**
   * Aggregates a list of authorization rejections into a single rejection. Prioritizes permission
   * rejections first, then tenant rejections, and finally returns the first rejection if no
   * specific type is found.
   *
   * @param rejections the list of collected authorization rejections
   * @return an {@link Either} containing a {@link Rejection} or {@link Void}
   */
  public static Either<Rejection, Void> aggregate(final List<AuthorizationRejection> rejections) {
    // return permission rejection first, if it exists
    final var permissionRejections =
        rejections.stream()
            .filter(AuthorizationRejection::isPermission)
            .map(AuthorizationRejection::rejection)
            .toList();
    if (!permissionRejections.isEmpty()) {
      final var reason =
          permissionRejections.stream()
              .map(Rejection::reason)
              .distinct()
              .collect(Collectors.joining("; "));
      return Either.left(new Rejection(RejectionType.FORBIDDEN, reason));
    }

    // if there are tenant rejections, return them
    final var tenantRejections =
        rejections.stream()
            .filter(AuthorizationRejection::isTenant)
            .map(AuthorizationRejection::rejection)
            .toList();
    if (!tenantRejections.isEmpty()) {
      final var reason =
          tenantRejections.stream()
              .map(Rejection::reason)
              .distinct()
              .collect(Collectors.joining("; "));
      // Use the first rejection type (should be FORBIDDEN or NOT_FOUND)
      return Either.left(new Rejection(tenantRejections.getFirst().type(), reason));
    }

    // Fallback: return the first rejection if present
    if (!rejections.isEmpty()) {
      return Either.left(rejections.getFirst().rejection());
    }

    // Should not happen, but fallback to forbidden
    return Either.left(
        new Rejection(RejectionType.FORBIDDEN, "Authorization failed for unknown reason"));
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
      return new Rejection(RejectionType.FORBIDDEN, "Authorization failed for unknown reason");
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
