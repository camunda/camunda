/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing.engine.service;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.DecisionDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.es.reader.DecisionDefinitionReader;
import org.camunda.optimize.service.importing.engine.service.definition.DecisionDefinitionResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings(UNCHECKED_CAST)
@ExtendWith(MockitoExtension.class)
public class DecisionDefinitionResolverServiceTest {

  private static final String TEST_KEY = "key";

  @Mock
  private DecisionDefinitionReader decisionDefinitionReader;

  @Mock
  private EngineContext engineContext;

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
    mockDecisionDefinitionsOnReaderLevel(id, version);

    // when
    final Optional<DecisionDefinitionOptimizeDto> decisionDefinition =
      underTest.getDefinition(id, engineContext);

    // then
    assertThat(decisionDefinition).isPresent();
    assertThat(decisionDefinition)
      .get()
      .extracting(DecisionDefinitionOptimizeDto::getVersion)
      .isEqualTo(version);
    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
  }

  @Test
  public void testDecisionDefinitionResultIsCached() {
    // given
    final String id = UUID.randomUUID().toString();
    final String version = "1";
    mockDecisionDefinitionsOnReaderLevel(id, version);

    // when
    final Optional<DecisionDefinitionOptimizeDto> firstDecisionDefinitionTry =
      underTest.getDefinition(id, engineContext);
    final Optional<DecisionDefinitionOptimizeDto> secondDecisionDefinitionTry =
      underTest.getDefinition(id, engineContext);

    // then
    assertThat(firstDecisionDefinitionTry).isPresent();
    assertThat(secondDecisionDefinitionTry).isPresent();
    assertThat(firstDecisionDefinitionTry).contains(secondDecisionDefinitionTry.get());

    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
  }

  @Test
  public void noCacheOrReaderHit_retrieveFromEngine() {
    // given
    final String id = UUID.randomUUID().toString();
    mockDecisionDefinitionsOnReaderLevel("otherId", "2");
    mockDecisionDefinitionForEngineContext(id, "1");

    // when
    final Optional<DecisionDefinitionOptimizeDto> definition =
      underTest.getDefinition(id, engineContext);

    // then
    assertThat(definition)
      .isPresent()
      .get()
      .extracting(
        DecisionDefinitionOptimizeDto::getId,
        DecisionDefinitionOptimizeDto::getKey,
        DecisionDefinitionOptimizeDto::getVersion
      )
      .containsExactly(id, TEST_KEY, "1");
    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
    verify(engineContext, times(1)).fetchDecisionDefinition(id);
  }

  @Test
  public void noCacheOrReaderHit_retrieveFromEngineAndAfterwardsFromCache() {
    // given
    final String id = UUID.randomUUID().toString();
    mockDecisionDefinitionsOnReaderLevel("otherId", "2");
    mockDecisionDefinitionForEngineContext(id, "1");

    // when
    final Optional<DecisionDefinitionOptimizeDto> firstDecisionDefinitionTry =
      underTest.getDefinition(id, engineContext);

    // then
    assertThat(firstDecisionDefinitionTry).isPresent();
    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
    verify(engineContext, times(1)).fetchDecisionDefinition(id);

    // when
    final Optional<DecisionDefinitionOptimizeDto> secondDecisionDefinitionTry =
      underTest.getDefinition(id, engineContext);
    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
    verify(engineContext, times(1)).fetchDecisionDefinition(id);
    assertThat(secondDecisionDefinitionTry).isPresent();
    assertThat(firstDecisionDefinitionTry).contains(secondDecisionDefinitionTry.get());
  }

  @Test
  public void testNoMatchingResult() {
    // given
    final String id = UUID.randomUUID().toString();
    mockDecisionDefinitionsOnReaderLevel("otherId", "1");
    when(engineContext.fetchDecisionDefinition(any())).thenReturn(null);

    // when
    final Optional<DecisionDefinitionOptimizeDto> definition =
      underTest.getDefinition(id, engineContext);

    // then
    assertThat(definition).isNotPresent();
    verify(decisionDefinitionReader, times(1)).getAllDecisionDefinitions();
    verify(engineContext, times(1)).fetchDecisionDefinition(id);
  }

  private void mockDecisionDefinitionsOnReaderLevel(final String id, final String version) {
    List<DecisionDefinitionOptimizeDto> mockedDefinitions = Lists.newArrayList(
      DecisionDefinitionOptimizeDto.builder()
        .id(id)
        .key(TEST_KEY)
        .version(version)
        .versionTag("aVersionTag")
        .name("name")
        .dataSource(new EngineDataSourceDto("engine"))
        .deleted(false)
        .dmn10Xml("")
        .build()
    );
    when(decisionDefinitionReader.getAllDecisionDefinitions()).thenReturn(mockedDefinitions);
  }

  private void mockDecisionDefinitionForEngineContext(final String id, final String version) {
    DecisionDefinitionOptimizeDto mockedDefinition =
      DecisionDefinitionOptimizeDto.builder()
        .id(id)
        .key(TEST_KEY)
        .version(version)
        .versionTag("aVersionTag")
        .name("name")
        .deleted(false)
        .dataSource(new EngineDataSourceDto("engine"))
        .dmn10Xml("")
        .build();
    when(engineContext.fetchDecisionDefinition(any())).thenReturn(mockedDefinition);
  }

}
