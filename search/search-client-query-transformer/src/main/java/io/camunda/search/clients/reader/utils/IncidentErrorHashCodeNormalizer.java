/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader.utils;

import static java.util.stream.Collectors.toMap;

import io.camunda.search.clients.reader.IncidentDocumentReader;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.Operator;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper that normalizes incident error hash code operations into error message operations by
 * resolving the hash codes via the IncidentDocumentReader.
 */
public class IncidentErrorHashCodeNormalizer {

  private static final Logger LOG = LoggerFactory.getLogger(IncidentErrorHashCodeNormalizer.class);

  private final IncidentDocumentReader incidentReader;

  public IncidentErrorHashCodeNormalizer(final IncidentDocumentReader incidentReader) {
    this.incidentReader = incidentReader;
  }

  public Optional<ProcessInstanceFilter> normalizeAndValidateProcessInstanceFilter(
      final ProcessInstanceFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    return normalizeProcessInstanceFilter(filter, resourceAccessChecks, new HashSet<>());
  }

  public Optional<ProcessDefinitionStatisticsFilter> normalizeAndValidateProcessDefinitionFilter(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks) {
    return normalizeProcessDefinitionFilter(filter, resourceAccessChecks, new HashSet<>());
  }

  private Optional<ProcessInstanceFilter> normalizeProcessInstanceFilter(
      final ProcessInstanceFilter filter,
      final ResourceAccessChecks resourceAccessChecks,
      final Set<ProcessInstanceFilter> visited) {
    if (filter == null) {
      logDropReason("process instance filter", "filter is null");
      return Optional.empty();
    }
    if (!visited.add(filter)) {
      logDropReason(
          "process instance filter", "cycle detected (filter already visited)", filter, visited);
      return Optional.empty();
    }

    final var hashOps = filter.incidentErrorHashCodeOperations();
    final var msgOps = filter.errorMessageOperations();
    final boolean hasHash = hashOps != null && !hashOps.isEmpty();
    final boolean hasMsg = msgOps != null && !msgOps.isEmpty();

    if (hasMsg && hasInvalidErrorMessages(msgOps)) {
      logDropReason("process instance filter", "invalid error message operations", msgOps);
      return Optional.empty();
    }
    if (hasHash) {
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process instance filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      final var combined = prependResolvedEq(resolvedOpt.get(), msgOps);
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(combined).build(),
          resourceAccessChecks,
          visited);
    }
    if (hasMsg) {
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(dedupeErrorMessages(msgOps)).build(),
          resourceAccessChecks,
          visited);
    }
    return normalizeOrFilters(filter, resourceAccessChecks, visited);
  }

  private Optional<ProcessInstanceFilter> normalizeOrFilters(
      final ProcessInstanceFilter filter,
      final ResourceAccessChecks resourceAccessChecks,
      final Set<ProcessInstanceFilter> visited) {
    final var orFilters = filter.orFilters();
    if (orFilters == null || orFilters.isEmpty()) {
      return Optional.of(filter);
    }
    final List<ProcessInstanceFilter> normalized = new ArrayList<>();
    for (final var sub : orFilters) {
      final var norm = normalizeProcessInstanceFilter(sub, resourceAccessChecks, visited);
      norm.ifPresent(normalized::add);
    }
    if (normalized.isEmpty()) {
      logDropReason(
          "process instance filter",
          "all orFilters were dropped after normalization",
          filter,
          orFilters);
      return Optional.empty();
    }
    return Optional.of(filter.toBuilder().orFilters(normalized).build());
  }

  private Optional<ProcessDefinitionStatisticsFilter> normalizeProcessDefinitionFilter(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks,
      final Set<ProcessDefinitionStatisticsFilter> visited) {
    if (filter == null) {
      logDropReason("process definition filter", "filter is null");
      return Optional.empty();
    }
    if (!visited.add(filter)) {
      logDropReason(
          "process definition filter", "cycle detected (filter already visited)", filter, visited);
      return Optional.empty();
    }

    final var hashOps = filter.incidentErrorHashCodeOperations();
    final var msgOps = filter.errorMessageOperations();
    final boolean hasHash = hashOps != null && !hashOps.isEmpty();
    final boolean hasMsg = msgOps != null && !msgOps.isEmpty();

    if (hasMsg && hasInvalidErrorMessages(msgOps)) {
      logDropReason("process definition filter", "invalid error message operations", msgOps);
      return Optional.empty();
    }
    if (hasHash) {
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process definition filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      final var combined = prependResolvedEq(resolvedOpt.get(), msgOps);
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(combined).build(),
          resourceAccessChecks,
          visited);
    }
    if (hasMsg) {
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(dedupeErrorMessages(msgOps)).build(),
          resourceAccessChecks,
          visited);
    }
    return normalizeOrFilters(filter, resourceAccessChecks, visited);
  }

  private Optional<ProcessDefinitionStatisticsFilter> normalizeOrFilters(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks,
      final Set<ProcessDefinitionStatisticsFilter> visited) {
    final var orFilters = filter.orFilters();
    if (orFilters == null || orFilters.isEmpty()) {
      return Optional.of(filter);
    }
    final List<ProcessDefinitionStatisticsFilter> normalized = new ArrayList<>();
    for (final var sub : orFilters) {
      final var norm = normalizeProcessDefinitionFilter(sub, resourceAccessChecks, visited);
      norm.ifPresent(normalized::add);
    }
    if (normalized.isEmpty()) {
      logDropReason(
          "process definition filter",
          "all orFilters were dropped after normalization",
          filter,
          orFilters);
      return Optional.empty();
    }
    return Optional.of(filter.toBuilder().orFilters(normalized).build());
  }

  private Optional<String> resolveErrorMessage(
      final List<Operation<Integer>> hashCodeOps, final ResourceAccessChecks resourceAccessChecks) {
    if (hashCodeOps == null || hashCodeOps.isEmpty()) {
      logDropReason("error message resolution", "hashCodeOps is null or empty", hashCodeOps);
      return Optional.empty();
    }
    final var resolved =
        incidentReader.findErrorMessageByErrorHashCodes(hashCodeOps, resourceAccessChecks);
    if (resolved == null || resolved.isBlank()) {
      logDropReason(
          "error message resolution",
          "resolved error message is null or blank",
          hashCodeOps,
          resolved);
      return Optional.empty();
    }
    return Optional.of(resolved);
  }

  private boolean hasInvalidErrorMessages(final List<Operation<String>> ops) {
    if (ops == null || ops.isEmpty()) {
      return true;
    }
    for (final var op : ops) {
      if (op == null) {
        return true;
      }
      final var operator = op.operator();
      if (operator == null) {
        return true;
      }
      if (operator == Operator.EXISTS || operator == Operator.NOT_EXISTS) {
        continue;
      }
      if (operator == Operator.IN || operator == Operator.NOT_IN) {
        final var values = op.values();
        if (values == null || values.isEmpty()) {
          return true;
        }
        for (final var v : values) {
          if (v == null || v.isBlank()) {
            return true;
          }
        }
        continue;
      }
      if (op.value() == null || Objects.requireNonNull(op.value()).isBlank()) {
        return true;
      }
    }
    return false;
  }

  private void logDropReason(
      final String filterType, final String reason, final Object... context) {
    if (context != null && context.length > 0) {
      LOG.warn("Dropping {}: {} Context: {}", filterType, reason, context);
    } else {
      LOG.warn("Dropping {}: {}", filterType, reason);
    }
  }

  private static List<Operation<String>> prependResolvedEq(
      final String resolved, final List<Operation<String>> existing) {
    final List<Operation<String>> combined = new ArrayList<>();
    combined.add(Operation.eq(resolved));
    if (existing != null && !existing.isEmpty()) {
      combined.addAll(existing);
    }
    return combined;
  }

  private List<Operation<String>> dedupeErrorMessages(final List<Operation<String>> ops) {
    if (ops == null || ops.isEmpty()) {
      return List.of();
    }
    return ops.stream()
        .filter(Objects::nonNull)
        .collect(toMap(Operation::value, op -> op, (op1, op2) -> op1, LinkedHashMap::new))
        .values()
        .stream()
        .toList();
  }
}
