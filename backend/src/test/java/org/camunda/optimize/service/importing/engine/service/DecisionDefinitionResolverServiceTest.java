/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class DecisionDefinitionResolverServiceTest {

  @Mock
  private DecisionDefinitionReader decisionDefinitionReader;

  private DecisionDefinitionResolverService underTest;

  @BeforeEach
  public void init() {
    this.underTest = new DecisionDefinitionResolverService(decisionDefinitionReader);
  }

  @Test
  public void testResolveVersionFromDecisionDefinitionReader() {
    // given
    final String id = UUID.randomUUID().toString();
    final String version = "2";
    mockDecisionDefinitions(id, version);

    //when
    final Optional<String> versionForDecisionDefinitionId = underTest.getVersionForDecisionDefinitionId(id);

    //then
    assertThat(versionForDecisionDefinitionId.isPresent(), is(true));
    assertThat(versionForDecisionDefinitionId.get(), is(version));
    verify(decisionDefinitionReader, times(1)).getDecisionDefinitions(false, false);
  }

  @Test
  public void testDecisionDefinitionResultIsCached() {
    // given
    final String id = UUID.randomUUID().toString();
    final String version = "1";
    mockDecisionDefinitions(id, version);

    //when
    final Optional<String> versionForDecisionDefinitionIdFirstTry = underTest.getVersionForDecisionDefinitionId(id);
    final Optional<String> versionForDecisionDefinitionIdSecondTry = underTest.getVersionForDecisionDefinitionId(id);

    //then
    assertThat(versionForDecisionDefinitionIdFirstTry.isPresent(), is(true));
    assertThat(versionForDecisionDefinitionIdSecondTry.isPresent(), is(true));
    assertThat(versionForDecisionDefinitionIdFirstTry.get(), is(versionForDecisionDefinitionIdSecondTry.get()));

    verify(decisionDefinitionReader, times(1)).getDecisionDefinitions(false, false);
  }

  @Test
  public void testNoMatchingResult() {
    // given
    final String id = UUID.randomUUID().toString();
    mockDecisionDefinitions("otherId", "1");

    //when
    final Optional<String> versionResult = underTest.getVersionForDecisionDefinitionId(id);

    //then
    assertThat(versionResult.isPresent(), is(false));
    verify(decisionDefinitionReader, times(1)).getDecisionDefinitions(false, false);
  }

  private void mockDecisionDefinitions(final String id, final String version) {
    List<DecisionDefinitionOptimizeDto> mockedDefinitions = Lists.newArrayList(
      new DecisionDefinitionOptimizeDto(id, "key", version, "aVersionTag", "name", "", "engine", null, null, null)
    );
    when(decisionDefinitionReader.getDecisionDefinitions(false, false)).thenReturn(mockedDefinitions);
  }

}
