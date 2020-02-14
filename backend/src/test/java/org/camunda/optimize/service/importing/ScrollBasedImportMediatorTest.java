/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.engine.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.importing.engine.handler.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.importing.engine.mediator.DecisionDefinitionXmlEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.importing.page.IdSetBasedImportPage;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class ScrollBasedImportMediatorTest {

  @Mock
  private ImportIndexHandlerRegistry importIndexHandlerRegistry;

  @Mock
  private BeanFactory beanFactory;

  @InjectMocks
  private DecisionDefinitionXmlEngineImportMediator underTest;

  @Mock
  private EngineContext engineContext;

  @Mock
  private DecisionDefinitionXmlFetcher engineEntityFetcher;

  @Mock
  private DecisionDefinitionXmlImportIndexHandler importIndexHandler;

  private ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();

  @Mock
  private ImportService<DecisionDefinitionXmlEngineDto> importService;

  @BeforeEach
  public void init() {
    when(beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext))
      .thenReturn(engineEntityFetcher);

    this.underTest = new DecisionDefinitionXmlEngineImportMediator(engineContext);
    this.underTest.importIndexHandlerRegistry = importIndexHandlerRegistry;
    this.underTest.beanFactory = beanFactory;
    this.underTest.configurationService = configurationService;
    this.underTest.init();
    this.underTest.importIndexHandler = importIndexHandler;
    this.underTest.importService = importService;
  }

  @Test
  public void testImportNextEnginePageWithEmptyIdSet() {
    // given
    IdSetBasedImportPage page = new IdSetBasedImportPage();
    page.setIds(new HashSet<>());
    when(importIndexHandler.getNextPage()).thenReturn(page);

    // when
    final boolean result = underTest.importNextPage();

    // then
    assertThat(result, is(false));
  }

  @Test
  public void testImportNextEnginePageWithNotEmptyIdSet() {
    // given
    IdSetBasedImportPage page = new IdSetBasedImportPage();
    Set<String> testIds = new HashSet<>();
    testIds.add("testID");
    testIds.add("testID2");
    page.setIds(testIds);
    when(importIndexHandler.getNextPage()).thenReturn(page);

    List<DecisionDefinitionXmlEngineDto> resultList = new ArrayList<>();
    resultList.add(new DecisionDefinitionXmlEngineDto());
    when(engineEntityFetcher.fetchXmlsForDefinitions(page))
      .thenReturn(resultList);

    // when
    final boolean result = underTest.importNextPage();

    // then
    assertThat(result, is(true));
    verify(importIndexHandler, times(1)).updateIndex(testIds.size());
    verify(importService, times(1)).executeImport(resultList);
  }

}