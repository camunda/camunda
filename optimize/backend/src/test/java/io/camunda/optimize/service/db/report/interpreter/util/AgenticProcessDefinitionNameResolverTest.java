/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.DefinitionType;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.service.DefinitionService;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.GroupByResult;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AgenticProcessDefinitionNameResolverTest {

  @Mock private DefinitionService definitionService;

  @Test
  void shouldLabelGroupWithLatestVersionNameWhileKeepingTheKey() {
    // given
    final CompositeCommandResult result = resultWithGroups("invoice-process");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "invoice-process"))
        .thenReturn(Optional.of(definitionWithName("Invoice Approval")));

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then
    final GroupByResult group = result.getGroups().getFirst();
    assertThat(group.getKey()).isEqualTo("invoice-process");
    assertThat(group.getLabel()).isEqualTo("Invoice Approval");
  }

  @Test
  void shouldFallBackToKeyWhenNoDefinitionIsFound() {
    // given
    final CompositeCommandResult result = resultWithGroups("orphan-process");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "orphan-process"))
        .thenReturn(Optional.empty());

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then the label falls back to the key
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("orphan-process");
  }

  @Test
  void shouldFallBackToKeyWhenLatestVersionHasNoName() {
    // given
    final CompositeCommandResult result = resultWithGroups("unnamed-process");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "unnamed-process"))
        .thenReturn(Optional.of(definitionWithName("   ")));

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then the blank name is ignored and the label falls back to the key
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("unnamed-process");
  }

  @Test
  void shouldResolveEachGroupIndependently() {
    // given
    final CompositeCommandResult result = resultWithGroups("heavy-process", "light-process");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "heavy-process"))
        .thenReturn(Optional.of(definitionWithName("Heavy Agent")));
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "light-process"))
        .thenReturn(Optional.empty());

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then each group is resolved on its own; unresolved keys keep the key as label
    assertThat(result.getGroups())
        .extracting(GroupByResult::getLabel)
        .containsExactly("Heavy Agent", "light-process");
  }

  @Test
  void shouldFallBackToKeyWhenLatestVersionNameIsNull() {
    // given
    final CompositeCommandResult result = resultWithGroups("nameless-process");
    when(definitionService.getLatestCachedDefinitionOnAnyTenant(
            DefinitionType.PROCESS, "nameless-process"))
        .thenReturn(Optional.of(definitionWithName(null)));

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then the null name is ignored and the label falls back to the key
    assertThat(result.getGroups().getFirst().getLabel()).isEqualTo("nameless-process");
  }

  @Test
  void shouldNotQueryDefinitionServiceForBlankKeys() {
    // given a group whose key is blank (nothing to resolve)
    final CompositeCommandResult result = resultWithGroups("   ");

    // when
    AgenticProcessDefinitionNameResolver.applyLatestVersionNameLabels(result, definitionService);

    // then the definition service is never consulted
    verifyNoInteractions(definitionService);
  }

  private static CompositeCommandResult resultWithGroups(final String... processDefinitionKeys) {
    final CompositeCommandResult result = new CompositeCommandResult(null, null);
    result.setGroups(
        List.of(processDefinitionKeys).stream()
            .map(key -> GroupByResult.createGroupByResult(key, Collections.emptyList()))
            .toList());
    return result;
  }

  private static ProcessDefinitionOptimizeDto definitionWithName(final String name) {
    return ProcessDefinitionOptimizeDto.builder().key("ignored").version("1").name(name).build();
  }
}
