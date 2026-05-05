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
import java.util.Locale;
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

    if (hasHash && hasMsg) {
      if (hasInvalidErrorMessages(msgOps)) {
        logDropReason("process instance filter", "invalid error message operations", msgOps);
        return Optional.empty();
      }
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process instance filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      final var resolved = resolvedOpt.get();
      if (!allOpsAcceptResolved(msgOps, resolved)) {
        logDropReason(
            "process instance filter",
            "resolved error message does not satisfy msgOps",
            msgOps,
            resolved);
        return Optional.empty();
      }
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(List.of(Operation.eq(resolved))).build(),
          resourceAccessChecks,
          visited);
    }
    if (hasHash) {
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process instance filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      return normalizeOrFilters(
          filter.toBuilder()
              .replaceErrorMessageOperations(List.of(Operation.eq(resolvedOpt.get())))
              .build(),
          resourceAccessChecks,
          visited);
    }
    if (hasMsg) {
      if (hasInvalidErrorMessages(msgOps)) {
        logDropReason("process instance filter", "invalid error message operations", msgOps);
        return Optional.empty();
      }
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

    if (hasHash && hasMsg) {
      if (hasInvalidErrorMessages(msgOps)) {
        logDropReason("process definition filter", "invalid error message operations", msgOps);
        return Optional.empty();
      }
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process definition filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      final var resolved = resolvedOpt.get();
      if (!allOpsAcceptResolved(msgOps, resolved)) {
        logDropReason(
            "process definition filter",
            "resolved error message does not satisfy msgOps",
            msgOps,
            resolved);
        return Optional.empty();
      }
      return normalizeOrFilters(
          filter.toBuilder().replaceErrorMessageOperations(List.of(Operation.eq(resolved))).build(),
          resourceAccessChecks,
          visited);
    }
    if (hasHash) {
      final var resolvedOpt = resolveErrorMessage(hashOps, resourceAccessChecks);
      if (resolvedOpt.isEmpty()) {
        logDropReason(
            "process definition filter", "could not resolve error message from hashOps", hashOps);
        return Optional.empty();
      }
      return normalizeOrFilters(
          filter.toBuilder()
              .replaceErrorMessageOperations(List.of(Operation.eq(resolvedOpt.get())))
              .build(),
          resourceAccessChecks,
          visited);
    }
    if (hasMsg) {
      if (hasInvalidErrorMessages(msgOps)) {
        logDropReason("process definition filter", "invalid error message operations", msgOps);
        return Optional.empty();
      }
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

  /**
   * Returns true if every op in {@code msgOps} accepts the {@code resolved} error message — i.e.,
   * the AND of the user-supplied error-message operations does not contradict the message uniquely
   * identified by the supplied incident-error-hash-code.
   */
  private boolean allOpsAcceptResolved(
      final List<Operation<String>> msgOps, final String resolved) {
    if (msgOps == null || msgOps.isEmpty() || resolved == null) {
      return false;
    }
    for (final var op : msgOps) {
      if (!opAcceptsResolved(op, resolved)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Evaluates an error-message {@link Operation} against the resolved message. {@code IN}/{@code
   * NOT_IN} use {@link String#contains} semantics to mirror the {@code match_phrase} behavior of
   * the Elasticsearch query (so a user-supplied truncated prefix is treated as a phrase contained
   * in the resolved message).
   */
  private static boolean opAcceptsResolved(final Operation<String> op, final String resolved) {
    if (op == null || op.operator() == null) {
      return false;
    }
    return switch (op.operator()) {
      case EQUALS -> resolved.equals(op.value());
      case NOT_EQUALS -> !resolved.equals(op.value());
      case IN -> {
        final var values = op.values();
        yield values != null
            && !values.isEmpty()
            && values.stream().anyMatch(v -> v != null && !v.isBlank() && resolved.contains(v));
      }
      case NOT_IN -> {
        final var values = op.values();
        // Defensive: blank values are filtered out so a stray "" in the list does not force a
        // rejection via resolved.contains("") == true. hasInvalidErrorMessages drops such ops
        // upstream, but the predicate stays correct in isolation.
        yield values != null
            && !values.isEmpty()
            && values.stream().filter(v -> v != null && !v.isBlank()).noneMatch(resolved::contains);
      }
      case LIKE -> likePatternMatches(op.value(), resolved);
      case EXISTS -> true;
      case NOT_EXISTS -> false;
      default -> false;
    };
  }

  private static boolean likePatternMatches(final String pattern, final String value) {
    if (pattern == null || value == null) {
      return false;
    }
    // Locale.ROOT keeps the lowercase mapping locale-independent — under Turkish/Azerbaijani
    // default locales, 'I'.toLowerCase() returns dotless 'ı' (U+0131), which would break
    // case-insensitive matching against ASCII error messages.
    return value.toLowerCase(Locale.ROOT).matches(globToRegex(pattern.toLowerCase(Locale.ROOT)));
  }

  private static String globToRegex(final String glob) {
    final var sb = new StringBuilder("^");
    for (int i = 0; i < glob.length(); i++) {
      final char c = glob.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '.', '(', ')', '[', ']', '{', '}', '+', '|', '^', '$', '\\' ->
            sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    return sb.append('$').toString();
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
      if (op.value() == null || op.value().isBlank()) {
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
