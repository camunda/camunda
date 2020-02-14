/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine.service;

import com.google.common.collect.Lists;
import org.camunda.optimize.dto.optimize.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.service.es.reader.ProcessDefinitionReader;
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
public class ProcessDefinitionResolverServiceTest {

  @Mock
  private ProcessDefinitionReader processDefinitionReader;

  private ProcessDefinitionResolverService underTest;

  @BeforeEach
  public void init() {
    this.underTest = new ProcessDefinitionResolverService(processDefinitionReader);
  }

  @Test
  public void testResolveDefinitionFromProcessDefinitionReader() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitions(id);

    //when
    final Optional<ProcessDefinitionOptimizeDto> processDefinition = underTest.getDefinitionForProcessDefinitionId(id);

    //then
    assertThat(processDefinition.isPresent(), is(true));
    assertThat(processDefinition.get().getId(), is(id));
    verify(processDefinitionReader, times(1)).getProcessDefinitions(false, false);
  }

  @Test
  public void testProcessDefinitionResultIsCached() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitions(id);

    //when
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionIdFirstTry = underTest.getDefinitionForProcessDefinitionId(id);
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionIdSecondTry = underTest.getDefinitionForProcessDefinitionId(id);

    //then
    assertThat(processDefinitionIdFirstTry.isPresent(), is(true));
    assertThat(processDefinitionIdSecondTry.isPresent(), is(true));
    assertThat(processDefinitionIdFirstTry.get().getId(), is(processDefinitionIdSecondTry.get().getId()));

    verify(processDefinitionReader, times(1)).getProcessDefinitions(false, false);
  }

  @Test
  public void testNoMatchingResult() {
    // given
    final String id = UUID.randomUUID().toString();
    mockProcessDefinitions("otherId");

    //when
    final Optional<ProcessDefinitionOptimizeDto> processDefinitionResult = underTest.getDefinitionForProcessDefinitionId(id);

    //then
    assertThat(processDefinitionResult.isPresent(), is(false));
    verify(processDefinitionReader, times(1)).getProcessDefinitions(false, false);
  }

  private void mockProcessDefinitions(final String id) {
    List<ProcessDefinitionOptimizeDto> mockedDefinitions = Lists.newArrayList(
      new ProcessDefinitionOptimizeDto(id, "key", "2", "aVersionTag", "name", "", "engine", null, null, null)
    );
    when(processDefinitionReader.getProcessDefinitions(false, false)).thenReturn(mockedDefinitions);
  }

}
