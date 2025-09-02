/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader;

import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper that normalizes incident error hash code operations into error message operations by
 * resolving the hash codes via the IncidentDocumentReader.
 */
public class IncidentErrorHashCodeNormalizer {

  private final IncidentDocumentReader incidentReader;

  public IncidentErrorHashCodeNormalizer(final IncidentDocumentReader incidentReader) {
    this.incidentReader = incidentReader;
  }

  public ProcessInstanceFilter normalizeProcessInstanceFilter(
      final ProcessInstanceFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    if (filter.incidentErrorHashCodeOperations() == null
        || filter.incidentErrorHashCodeOperations().isEmpty()) {
      return filter;
    }

    final var resolvedErrorMessage =
        incidentReader.findErrorMessageByErrorHashCodes(
            filter.incidentErrorHashCodeOperations(), resourceAccessChecks);

    if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
      // If the error hash code cannot be resolved, return a filter that will be detected as invalid
      return filter.toBuilder()
          .replaceIncidentErrorHashCodeOperations(List.of())
          .replaceErrorMessageOperations(List.of())
          .build();
    }

    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCodeOperations(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  public List<ProcessInstanceFilter> normalizeProcessInstanceOrFilters(
      final List<ProcessInstanceFilter> orFilters,
      final ResourceAccessChecks resourceAccessChecks) {
    final List<ProcessInstanceFilter> normalized = new ArrayList<>();
    for (final var subFilter : orFilters) {
      if (subFilter.incidentErrorHashCodeOperations() == null
          || subFilter.incidentErrorHashCodeOperations().isEmpty()) {
        normalized.add(subFilter);
        continue;
      }

      final var resolvedErrorMessage =
          incidentReader.findErrorMessageByErrorHashCodes(
              subFilter.incidentErrorHashCodeOperations(), resourceAccessChecks);

      if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
        // If the error hash code cannot be resolved, skip this subfilter (do not add to normalized)
        continue;
      }

      final var existingOps =
          subFilter.errorMessageOperations() != null
              ? new ArrayList<>(subFilter.errorMessageOperations())
              : new ArrayList<Operation<String>>();

      existingOps.add(Operation.eq(resolvedErrorMessage));

      final var updatedSubFilter =
          subFilter.toBuilder()
              .incidentErrorHashCodeOperations(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }

  public ProcessDefinitionStatisticsFilter normalizeProcessDefinitionFilter(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks) {
    if (filter.incidentErrorHashCodeOperations() == null
        || filter.incidentErrorHashCodeOperations().isEmpty()) {
      return filter;
    }

    final var resolvedErrorMessage =
        incidentReader.findErrorMessageByErrorHashCodes(
            filter.incidentErrorHashCodeOperations(), resourceAccessChecks);

    if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
      return filter.toBuilder().incidentErrorHashCodeOperations(List.of()).build();
    }

    final var existingOps =
        filter.errorMessageOperations() != null
            ? new ArrayList<>(filter.errorMessageOperations())
            : new ArrayList<Operation<String>>();

    existingOps.add(Operation.eq(resolvedErrorMessage));

    return filter.toBuilder()
        .incidentErrorHashCodeOperations(null)
        .replaceErrorMessageOperations(existingOps)
        .build();
  }

  public List<ProcessDefinitionStatisticsFilter> normalizeProcessDefinitionOrFilters(
      final List<ProcessDefinitionStatisticsFilter> orFilters,
      final ResourceAccessChecks resourceAccessChecks) {
    final List<ProcessDefinitionStatisticsFilter> normalized = new ArrayList<>();
    for (final var subFilter : orFilters) {
      if (subFilter.incidentErrorHashCodeOperations() == null
          || subFilter.incidentErrorHashCodeOperations().isEmpty()) {
        normalized.add(subFilter);
        continue;
      }

      final var resolvedErrorMessage =
          incidentReader.findErrorMessageByErrorHashCodes(
              subFilter.incidentErrorHashCodeOperations(), resourceAccessChecks);

      if (resolvedErrorMessage == null || resolvedErrorMessage.isBlank()) {
        continue;
      }

      final var existingOps =
          subFilter.errorMessageOperations() != null
              ? new ArrayList<>(subFilter.errorMessageOperations())
              : new ArrayList<Operation<String>>();

      existingOps.add(Operation.eq(resolvedErrorMessage));

      final var updatedSubFilter =
          subFilter.toBuilder()
              .incidentErrorHashCodeOperations(null)
              .replaceErrorMessageOperations(existingOps)
              .build();

      normalized.add(updatedSubFilter);
    }
    return normalized;
  }

  /**
   * Normalizes and validates a ProcessInstanceFilter, including incident error hash codes and OR
   * filters. Returns null only if the filter is truly invalid (e.g., all OR filters resolved to
   * empty). If the filter is simply empty (no error hash code, no error message, no OR filter),
   * returns the filter as-is.
   */
  public ProcessInstanceFilter normalizeAndValidateProcessInstanceFilter(
      final ProcessInstanceFilter filter, final ResourceAccessChecks resourceAccessChecks) {
    // Step 1: Normalize incident error hash codes
    ProcessInstanceFilter normalizedFilter =
        normalizeProcessInstanceFilter(filter, resourceAccessChecks);

    if (filter.incidentErrorHashCodeOperations() != null
        && !filter.incidentErrorHashCodeOperations().isEmpty()
        && (normalizedFilter.errorMessageOperations() == null
            || normalizedFilter.errorMessageOperations().isEmpty())
        && (normalizedFilter.incidentErrorHashCodeOperations() == null
            || normalizedFilter.incidentErrorHashCodeOperations().isEmpty())) {
      // If the original filter had error hash codes but the normalized filter has no error message
      // operations and no remaining error hash code operations, and no OR filters, it means the
      // error hash codes could not be resolved. Thus, the filter is invalid.
      return null;
    }
    ;

    // Step 2: Normalize OR filters
    if (normalizedFilter.orFilters() != null && !normalizedFilter.orFilters().isEmpty()) {
      final List<ProcessInstanceFilter> normalizedOr =
          normalizeProcessInstanceOrFilters(normalizedFilter.orFilters(), resourceAccessChecks);
      if (normalizedOr.isEmpty()) {
        // All OR filters resolved to empty: truly invalid
        return null;
      }
      normalizedFilter = normalizedFilter.toBuilder().orFilters(normalizedOr).build();
    }

    // Only return null if all OR filters are empty (handled above)
    // Otherwise, return the normalized filter (even if empty)
    return normalizedFilter;
  }

  /**
   * Normalizes and validates a ProcessDefinitionStatisticsFilter, including incident error hash
   * codes and OR filters. Returns null only if the filter is truly invalid (e.g., all OR filters
   * resolved to empty). If the filter is simply empty (no error hash code, no error message, no OR
   * filter), returns the filter as-is.
   */
  public ProcessDefinitionStatisticsFilter normalizeAndValidateProcessDefinitionFilter(
      final ProcessDefinitionStatisticsFilter filter,
      final ResourceAccessChecks resourceAccessChecks) {
    // Step 1: Normalize incident error hash codes
    ProcessDefinitionStatisticsFilter normalizedFilter =
        normalizeProcessDefinitionFilter(filter, resourceAccessChecks);

    if (filter.incidentErrorHashCodeOperations() != null
        && !filter.incidentErrorHashCodeOperations().isEmpty()
        && (normalizedFilter.errorMessageOperations() == null
            || normalizedFilter.errorMessageOperations().isEmpty())
        && (normalizedFilter.incidentErrorHashCodeOperations() == null
            || normalizedFilter.incidentErrorHashCodeOperations().isEmpty())) {
      // If the original filter had error hash codes but the normalized filter has no error message
      // operations and no remaining error hash code operations, and no OR filters, it means the
      // error hash codes could not be resolved. Thus, the filter is invalid.
      return null;
    }

    // Step 2: Normalize OR filters
    if (normalizedFilter.orFilters() != null && !normalizedFilter.orFilters().isEmpty()) {
      final List<ProcessDefinitionStatisticsFilter> normalizedOr =
          normalizeProcessDefinitionOrFilters(normalizedFilter.orFilters(), resourceAccessChecks);
      if (normalizedOr.isEmpty()) {
        // All OR filters resolved to empty: truly invalid
        return null;
      }
      normalizedFilter = normalizedFilter.toBuilder().orFilters(normalizedOr).build();
    }

    // Only return null if all OR filters are empty (handled above)
    // Otherwise, return the normalized filter (even if empty)
    return normalizedFilter;
  }
}
