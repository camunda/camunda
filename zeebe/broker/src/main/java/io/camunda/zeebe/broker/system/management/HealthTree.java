/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.management;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

// Don't serialize Optional if missing
@JsonInclude(Include.NON_ABSENT)
public record HealthTree(
    String id,
    String name,
    HealthStatus status,
    Optional<String> message,
    // Iso8601 format
    @JsonFormat(shape = Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
        Optional<Instant> since,
    Optional<HealthStatus> componentsState,
    Collection<HealthTree> children) {

  public static HealthTree fromHealthReport(final HealthReport report) {
    return fromHealthReport(report.getComponentName(), report);
  }

  public static HealthTree fromHealthReport(final String id, final HealthReport report) {
    final var mappedChildren =
        report.children().entrySet().stream()
            .map(entry -> HealthTree.fromHealthReport(entry.getKey(), entry.getValue()))
            .toList();
    final var componentStatus =
        mappedChildren.stream().map(HealthTree::status).max(HealthStatus.COMPARATOR);
    final var issue = java.util.Optional.ofNullable(report.getIssue());
    return new HealthTree(
        id,
        report.getComponentName(),
        componentStatus.map(status -> report.status().combine(status)).orElse(report.status()),
        issue.map(HealthIssue::message),
        issue.map(HealthIssue::since),
        componentStatus,
        mappedChildren);
  }
}
