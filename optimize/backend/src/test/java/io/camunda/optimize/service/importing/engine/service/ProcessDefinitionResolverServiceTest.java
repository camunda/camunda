/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.engine.service;

import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.common.collect.Lists;
import io.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import io.camunda.optimize.dto.optimize.datasource.EngineDataSourceDto;
import io.camunda.optimize.rest.engine.EngineContext;
import io.camunda.optimize.service.db.DatabaseClient;
import io.camunda.optimize.service.db.reader.ProcessDefinitionReader;
import io.camunda.optimize.service.importing.engine.service.definition.ProcessDefinitionResolverService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings(UNCHECKED_CAST)
@ExtendWith(MockitoExtension.class)
public class ProcessDefinitionResolverServiceTest {

  private static final String TEST_KEY = "key";

  @Mock
  private ProcessDefinitionReader processDefinitionReader;

  @Mock
  private DatabaseClient databaseClient;

  @Mock
  private EngineContext engineContext;

  private ProcessDefinitionResolverService underTest;

  @BeforeEach
  public void init() {
    underTest = new ProcessDefinitionResolverService(databaseClient, processDefinitionReader);
  }

  @Test
  public void testResolveDefinitionFromProcessDefinitionReader() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitionsOnReaderLevel(id);

    // when
    final Optional<ProcessDefinitionOptimizeDto> decisionDefinition =
        underTest.getDefinition(id, engineContext);

    // then
    assertThat(decisionDefinition).isPresent();
    assertThat(decisionDefinition)
        .get()
        .extracting(ProcessDefinitionOptimizeDto::getId)
        .isEqualTo(id);
    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
  }

  @Test
  public void testProcessDefinitionResultIsCached() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitionsOnReaderLevel(id);

    // when
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionFirstTry =
        underTest.getDefinition(id, engineContext);
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionSecondTry =
        underTest.getDefinition(id, engineContext);

    // then
    assertThat(processDefinitionFirstTry).isPresent();
    assertThat(processDefinitionSecondTry).isPresent();
    assertThat(processDefinitionFirstTry.get().getId())
        .isEqualTo(processDefinitionSecondTry.get().getId());

    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
  }

  @Test
  @SuppressWarnings("checkstyle:methodname")
  public void noCacheOrReaderHit_retrieveFromEngine() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitionsOnReaderLevel("otherId");
    mockProcessDefinitionForEngineContext(id);

    // when
    final Optional<ProcessDefinitionOptimizeDto> definition =
        underTest.getDefinition(id, engineContext);

    // then
    assertThat(definition)
        .isPresent()
        .get()
        .extracting(ProcessDefinitionOptimizeDto::getId, ProcessDefinitionOptimizeDto::getKey)
        .containsExactly(id, TEST_KEY);
    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
    verify(engineContext, times(1)).fetchProcessDefinition(id);
  }

  @Test
  @SuppressWarnings("checkstyle:methodname")
  public void noCacheOrReaderHit_retrieveFromEngineAndAfterwardsFromCache() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitionsOnReaderLevel("otherId");
    mockProcessDefinitionForEngineContext(id);

    // when
    final Optional<ProcessDefinitionOptimizeDto> firstProcessDefinitionTry =
        underTest.getDefinition(id, engineContext);

    // then
    assertThat(firstProcessDefinitionTry).isPresent();
    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
    verify(engineContext, times(1)).fetchProcessDefinition(id);

    // when
    final Optional<ProcessDefinitionOptimizeDto> secondProcessDefinitionTry =
        underTest.getDefinition(id, engineContext);
    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
    verify(engineContext, times(1)).fetchProcessDefinition(id);
    assertThat(secondProcessDefinitionTry).isPresent();
    assertThat(firstProcessDefinitionTry).contains(secondProcessDefinitionTry.get());
  }

  @Test
  public void testNoMatchingResult() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitionsOnReaderLevel("otherId");
    when(engineContext.fetchProcessDefinition(any())).thenReturn(null);

    // when
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionResult =
        underTest.getDefinition(id, engineContext);

    // then
    assertThat(processDefinitionResult).isNotPresent();
    verify(processDefinitionReader, times(1)).getAllProcessDefinitions();
    verify(engineContext, times(1)).fetchProcessDefinition(id);
  }

  private void mockProcessDefinitionsOnReaderLevel(final String id) {
    final List<ProcessDefinitionOptimizeDto> mockedDefinitions =
        Lists.newArrayList(
            ProcessDefinitionOptimizeDto.builder()
                .id(id)
                .version("2")
                .versionTag("aVersionTag")
                .name("name")
                .dataSource(new EngineDataSourceDto(""))
                .deleted(false)
                .build());
    when(processDefinitionReader.getAllProcessDefinitions()).thenReturn(mockedDefinitions);
  }

  private void mockProcessDefinitionForEngineContext(final String id) {
    final ProcessDefinitionOptimizeDto mockedDefinition =
        new ProcessDefinitionOptimizeDto(
            id,
            TEST_KEY,
            "1",
            "aVersionTag",
            "name",
            new EngineDataSourceDto(""),
            "engine",
            null,
            false,
            true,
            null,
            null);
    when(engineContext.fetchProcessDefinition(any())).thenReturn(mockedDefinition);
  }
}
