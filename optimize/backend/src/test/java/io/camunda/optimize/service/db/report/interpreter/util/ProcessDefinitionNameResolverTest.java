/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.optimize.dto.optimize.query.report.single.ReportDataDefinitionDto;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProcessDefinitionNameResolverTest {

  @Test
  void shouldLabelGroupWithDisplayNameWhileKeepingTheKey() {
    // given
    final CompositeCommandResult result = resultWithGroups("invoice-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result, List.of(definition("invoice-process", "Invoice", "Invoice Approval")));

    // then
    final GroupByResult group = result.getGroups().getFirst();
    assertThat(group.getKey()).isEqualTo("invoice-process");
    assertThat(group.getLabel()).isEqualTo("Invoice Approval");
  }

  @Test
  void shouldFallBackToNameWhenDisplayNameIsMissing() {
    // given
    final CompositeCommandResult result = resultWithGroups("null-display", "blank-display");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result,
        List.of(
            definition("null-display", "Null Display", null),
            definition("blank-display", "Blank Display", "   ")));

    // then
    assertThat(result.getGroups())
        .extracting(GroupByResult::getLabel)
        .containsExactly("Null Display", "Blank Display");
  }

  @Test
  void shouldFallBackToKeyWhenDefinitionHasNoName() {
    // given
    final CompositeCommandResult result = resultWithGroups("nameless-process", "blank-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result,
        List.of(
            definition("nameless-process", null, null), definition("blank-process", "  ", " ")));

    // then no label is ever blank
    assertThat(result.getGroups())
        .extracting(GroupByResult::getLabel)
        .containsExactly("nameless-process", "blank-process");
  }

  @Test
  void shouldFallBackToKeyWhenReportHasNoMatchingDefinition() {
    // given a key the report's definitions do not cover, e.g. one from a tenant the user cannot see
    final CompositeCommandResult result = resultWithGroups("orphan-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result, List.of(definition("other-process", "Other Process", "Other Process")));

    // then the label is never borrowed from another definition
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("orphan-process");
  }

  @Test
  void shouldFallBackToKeyWhenReportHasNoDefinitions() {
    // given
    final CompositeCommandResult result = resultWithGroups("some-process", "other-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(result, null);
    ProcessDefinitionNameResolver.applyDefinitionLabels(result, List.of());

    // then
    assertThat(result.getGroups())
        .extracting(GroupByResult::getLabel)
        .containsExactly("some-process", "other-process");
  }

  @Test
  void shouldResolveEachGroupIndependently() {
    // given
    final CompositeCommandResult result = resultWithGroups("heavy-process", "light-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result, List.of(definition("heavy-process", "Heavy Agent", "Heavy Agent")));

    // then
    assertThat(result.getGroups())
        .extracting(GroupByResult::getLabel)
        .containsExactly("Heavy Agent", "light-process");
  }

  @Test
  void shouldUseFirstDefinitionWhenKeyAppearsTwice() {
    // given
    final CompositeCommandResult result = resultWithGroups("invoice-process");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result,
        List.of(
            definition("invoice-process", "Invoice First", "Invoice First"),
            definition("invoice-process", "Invoice Second", "Invoice Second")));

    // then
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("Invoice First");
  }

  @Test
  void shouldLeaveBlankKeysUntouched() {
    // given
    final CompositeCommandResult result = resultWithGroups("   ");

    // when
    ProcessDefinitionNameResolver.applyDefinitionLabels(
        result, List.of(definition("   ", "Blank Key", "Blank Key")));

    // then
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("   ");
  }

  private static CompositeCommandResult resultWithGroups(final String... processDefinitionKeys) {
    final CompositeCommandResult result = new CompositeCommandResult(null, null);
    result.setGroups(
        List.of(processDefinitionKeys).stream()
            .map(key -> GroupByResult.createGroupByResult(key, Collections.emptyList()))
            .toList());
    return result;
  }

  private static ReportDataDefinitionDto definition(
      final String key, final String name, final String displayName) {
    final ReportDataDefinitionDto definition = new ReportDataDefinitionDto(key);
    definition.setName(name);
    definition.setDisplayName(displayName);
    return definition;
  }
}
