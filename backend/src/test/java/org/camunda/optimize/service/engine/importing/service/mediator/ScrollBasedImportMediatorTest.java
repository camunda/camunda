/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.DecisionDefinitionXmlEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.DecisionDefinitionXmlFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.engine.importing.index.handler.impl.DecisionDefinitionXmlImportIndexHandler;
import org.camunda.optimize.service.engine.importing.index.page.IdSetBasedImportPage;
import org.camunda.optimize.service.engine.importing.service.ImportService;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ScrollBasedImportMediatorTest {

  @Mock
  private ImportIndexHandlerProvider provider;

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

  @Before
  public void init() {
    when(beanFactory.getBean(DecisionDefinitionXmlFetcher.class, engineContext))
      .thenReturn(engineEntityFetcher);

    this.underTest = new DecisionDefinitionXmlEngineImportMediator(engineContext);
    this.underTest.provider = provider;
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
    final boolean result = underTest.importNextEnginePage();

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
    final boolean result = underTest.importNextEnginePage();

    // then
    assertThat(result, is(true));
    verify(importIndexHandler, times(1)).updateIndex(testIds.size());
    verify(importService, times(1)).executeImport(resultList);
  }

}