/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.importing.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class DecisionDefinitionVersionResolverServiceTest {

  @Mock
  private DecisionDefinitionReader decisionDefinitionReader;

  private DecisionDefinitionVersionResolverService underTest;

  @Before
  public void init() {
    this.underTest = new DecisionDefinitionVersionResolverService(decisionDefinitionReader);
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
