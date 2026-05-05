/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.reader.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.search.clients.reader.IncidentDocumentReader;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.security.reader.ResourceAccessChecks;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class IncidentErrorHashCodeNormalizerTest {

  private IncidentDocumentReader incidentReader;
  private IncidentErrorHashCodeNormalizer normalizer;
  private ResourceAccessChecks resourceAccessChecks;

  @BeforeEach
  void setUp() {
    incidentReader = mock(IncidentDocumentReader.class);
    normalizer = new IncidentErrorHashCodeNormalizer(incidentReader);
    resourceAccessChecks = mock(ResourceAccessChecks.class);
  }

  @Test
  void shouldResolveErrorHashCodeForProcessInstance() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("ResolvedError");

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).hasSize(1);
    assertThat(result.get().errorMessageOperations().getFirst().value()).isEqualTo("ResolvedError");
  }

  @Test
  void shouldReturnEmptyIfUnresolvedHashCodeForProcessInstance() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleOrFiltersForProcessInstance() {
    final var subFilter1 =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var subFilter2 =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessInstanceFilter.Builder().orFilters(List.of(subFilter1, subFilter2)).build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Error1");
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result).isPresent();
    assertThat(result.get().orFilters()).hasSize(1);
    assertThat(result.get().orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Error1");
  }

  @Test
  void shouldResolveErrorHashCodeForProcessDefinition() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("ResolvedError");

    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).hasSize(1);
    assertThat(result.get().errorMessageOperations().getFirst().value()).isEqualTo("ResolvedError");
  }

  @Test
  void shouldReturnEmptyIfUnresolvedHashCodeForProcessDefinition() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result).isEmpty();
  }

  @Test
  void shouldHandleOrFiltersForProcessDefinition() {
    final var subFilter1 =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var subFilter2 =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(22012345678900L)
            .orFilters(List.of(subFilter1, subFilter2))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Error1");
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);

    assertThat(result).isPresent();
    assertThat(result.get().orFilters()).hasSize(1);
    assertThat(result.get().orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Error1");
  }

  @Test
  void shouldNormalizeToResolvedErrorMessageWhenHashSuppliedAndResolves() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenHashSuppliedAndDoesNotResolveAndNoErrorMessage() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeToDedupeErrorMessageWhenHashAndMatchingErrorMessageSupplied() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenHashAndErrorMessageSuppliedButNotEqual() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Other")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  // Regression test for https://github.com/camunda/camunda/issues/45129:
  // when the IN list contains a truncated prefix of the resolved error message, the prefix
  // alone phrase-matches every incident with that prefix in Elasticsearch, so the hash filter
  // is effectively ignored. The normalizer must collapse to eq(resolved) instead of
  // in([prefix, resolved]) so that the hash filter narrows results correctly.
  @Test
  void shouldNormalizeInOpToEqResolvedWhenInValueIsTruncatedPrefixOfResolved() {
    final var resolved =
        "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
    final var truncatedPrefix = resolved.substring(0, 50);
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of(truncatedPrefix))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn(resolved);

    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);

    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq(resolved));
  }

  @Test
  void shouldReturnEmptyWhenInValuesAreNotContainedInResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of("unrelated"))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeInOpToEqResolvedWhenInContainsBothUnrelatedAndResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of("Other message", "Resolved"))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenNeqEqualsResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.neq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeNeqToEqResolvedWhenNeqValueDiffersFromResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.neq("Other")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenNotInContainsValueContainedInResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.notIn(List.of("Resolved"))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeLikeToEqResolvedWhenLikePatternMatchesResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("Expected*")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Expected result of the expression");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations())
        .containsExactly(Operation.eq("Expected result of the expression"));
  }

  @Test
  void shouldReturnEmptyWhenLikePatternDoesNotMatchResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("Failed*")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Expected result of the expression");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  // resolved.contains("") is always true, so a stray blank value in the IN list must not be
  // allowed to silently satisfy the AND — the user's other (non-blank) IN constraints have to
  // remain authoritative. The normalizer drops the filter as invalid instead.
  @Test
  void shouldReturnEmptyWhenInListContainsBlankValueAlongsideOthers() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of("Other", ""))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenNotInListContainsBlankValueAlongsideOthers() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.notIn(List.of("Other", ""))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeNotInToEqResolvedWhenNoneOfItsValuesAreContainedInResolved() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.notIn(List.of("Other", "Different"))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldNormalizeExistsToEqResolvedWhenCombinedWithHash() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.exists(true)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenNotExistsCombinedWithHash() {
    // resolveErrorMessage guarantees R is non-blank, so NOT_EXISTS contradicts the hash filter.
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.exists(false)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldMatchLikePatternWithSingleCharWildcard() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("Err?r")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Error");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Error"));
  }

  @Test
  void shouldMatchLikePatternCaseInsensitively() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("EXPECTED*")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Expected result");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations())
        .containsExactly(Operation.eq("Expected result"));
  }

  // Regression guard for globToRegex: regex meta-characters in the LIKE pattern must be escaped,
  // so e.g. "a.b" matches the literal "a.b" rather than "a" + any-char + "b".
  @Test
  void shouldEscapeRegexMetaCharactersInLikePattern() {
    final var matchingFilter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("a.b")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("a.b");
    final var matching =
        normalizer.normalizeAndValidateProcessInstanceFilter(matchingFilter, resourceAccessChecks);
    assertThat(matching).isPresent();

    // "axb" must NOT match the pattern "a.b" — the dot is a literal, not a regex any-char.
    final var nonMatchingFilter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("a.b")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("axb");
    final var nonMatching =
        normalizer.normalizeAndValidateProcessInstanceFilter(
            nonMatchingFilter, resourceAccessChecks);
    assertThat(nonMatching).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenInValuesListIsEmpty() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of())))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashResolvesButErrorMessageIsInvalid() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq(" "))) // blank
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashDoesNotResolveAndErrorMessageSupplied() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeToErrorMessageWhenOnlyErrorMessageSupplied() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyFilterWhenNoHashAndNoErrorMessageSupplied() {
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashDoesNotResolveAndErrorMessageIsInvalid() {
    final var filter =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq(" ")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfAllOrFiltersInvalid() {
    final var invalid1 =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var invalid2 =
        new ProcessInstanceFilter.Builder()
            .errorMessageOperations(List.of(Operation.eq(" ")))
            .build();
    final var filter =
        new ProcessInstanceFilter.Builder().orFilters(List.of(invalid1, invalid2)).build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnOnlyValidOrFilters() {
    final var valid =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var invalid =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessInstanceFilter.Builder().orFilters(List.of(valid, invalid)).build();
    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1234)),
            any()))
        .thenReturn("Resolved");
    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1111)),
            any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().orFilters()).hasSize(1);
    assertThat(result.get().orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Resolved");
  }

  @Test
  void shouldReturnEmptyIfTopLevelInvalidEvenIfOrValid() {
    final var valid =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var topLevel =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .orFilters(List.of(valid))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(topLevel, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnValidIfTopLevelValidAndOrInvalid() {
    final var invalid =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var topLevel =
        new ProcessInstanceFilter.Builder()
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .orFilters(List.of(invalid))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1234)),
            any()))
        .thenReturn("Resolved");

    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1111)),
            any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessInstanceFilter(topLevel, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  // --- ProcessDefinitionStatisticsFilter: Truth Table & ORs ---

  @Test
  void shouldNormalizeToResolvedErrorMessageWhenHashSuppliedAndResolvesDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenHashSuppliedAndDoesNotResolveAndNoErrorMessageDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeToDedupeErrorMessageWhenHashAndMatchingErrorMessageSuppliedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenHashAndErrorMessageSuppliedButNotEqualDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Other")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeInOpToEqResolvedWhenInValueIsTruncatedPrefixOfResolvedDefStat() {
    final var resolved =
        "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
    final var truncatedPrefix = resolved.substring(0, 50);
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of(truncatedPrefix))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn(resolved);
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq(resolved));
  }

  @Test
  void shouldReturnEmptyWhenInValuesAreNotContainedInResolvedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of("unrelated"))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenNeqEqualsResolvedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.neq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeLikeToEqResolvedWhenLikePatternMatchesResolvedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("Expected*")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Expected result of the expression");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations())
        .containsExactly(Operation.eq("Expected result of the expression"));
  }

  @Test
  void shouldReturnEmptyWhenLikePatternDoesNotMatchResolvedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("Failed*")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Expected result of the expression");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenInListContainsBlankValueAlongsideOthersDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.in(List.of("Other", ""))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenNotInListContainsBlankValueAlongsideOthersDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.notIn(List.of("Other", ""))))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeExistsToEqResolvedWhenCombinedWithHashDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.exists(true)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyWhenNotExistsCombinedWithHashDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.exists(false)))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("Resolved");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldEscapeRegexMetaCharactersInLikePatternDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.like("a.b")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("axb");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashResolvesButErrorMessageIsInvalidDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq(" ")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashDoesNotResolveAndErrorMessageSuppliedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldNormalizeToErrorMessageWhenOnlyErrorMessageSuppliedDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(List.of(Operation.eq("Resolved")))
            .build();
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).containsExactly(Operation.eq("Resolved"));
  }

  @Test
  void shouldReturnEmptyFilterWhenNoHashAndNoErrorMessageSuppliedDefStat() {
    final var filter = new ProcessDefinitionStatisticsFilter.Builder(1L).build();
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().errorMessageOperations()).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenHashDoesNotResolveAndErrorMessageIsInvalidDefStat() {
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .errorMessageOperations(List.of(Operation.eq(" ")))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1234))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyIfAllOrFiltersInvalidDefStat() {
    final var invalid1 =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var invalid2 =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .errorMessageOperations(List.of(Operation.eq(" ")))
            .build();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .orFilters(List.of(invalid1, invalid2))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(eq(List.of(Operation.eq(1111))), any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnOnlyValidOrFiltersDefStat() {
    final var valid =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1234)))
            .build();
    final var invalid =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .incidentErrorHashCodeOperations(List.of(Operation.eq(1111)))
            .build();
    final var filter =
        new ProcessDefinitionStatisticsFilter.Builder(1L)
            .orFilters(List.of(valid, invalid))
            .build();
    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1234)),
            any()))
        .thenReturn("Resolved");
    when(incidentReader.findErrorMessageByErrorHashCodes(
            argThat(
                ops ->
                    ops != null
                        && !ops.isEmpty()
                        && ops.getFirst().value() != null
                        && Objects.equals(ops.getFirst().value(), 1111)),
            any()))
        .thenReturn("");
    final var result =
        normalizer.normalizeAndValidateProcessDefinitionFilter(filter, resourceAccessChecks);
    assertThat(result).isPresent();
    assertThat(result.get().orFilters()).hasSize(1);
    assertThat(result.get().orFilters().getFirst().errorMessageOperations().getFirst().value())
        .isEqualTo("Resolved");
  }
}
