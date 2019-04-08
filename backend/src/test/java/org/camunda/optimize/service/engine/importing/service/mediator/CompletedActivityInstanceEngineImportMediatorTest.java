/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.engine.importing.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.engine.importing.index.handler.ImportIndexHandlerProvider;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class CompletedActivityInstanceEngineImportMediatorTest extends TimestampBasedImportMediatorTest {


  @Mock
  private ImportIndexHandlerProvider provider;

  @Mock
  private BeanFactory beanFactory;

  @Mock
  private EngineContext engineContext;

  @Mock
  private CompletedActivityInstanceFetcher engineEntityFetcher;

  private ConfigurationService configurationService = new ConfigurationService();

  @Before
  public void init() {
    when(beanFactory.getBean(CompletedActivityInstanceFetcher.class, engineContext))
      .thenReturn(engineEntityFetcher);

    this.underTest = new CompletedActivityInstanceEngineImportMediator(engineContext);
    this.underTest.provider = provider;
    this.underTest.beanFactory = beanFactory;
    this.underTest.configurationService = configurationService;
    this.underTest.init();

    super.init();
  }

  @Test
  public void testImportNextEnginePage_returnsFalse() {
    // when
    final boolean result = underTest.importNextEnginePage();

    // then
    assertThat(result, is(false));
  }

  @Test
  public void testImportNextEnginePage_returnsTrue() {
    // given
    List<HistoricActivityInstanceEngineDto> engineResultList = new ArrayList<>();
    engineResultList.add(new HistoricActivityInstanceEngineDto());
    when(engineEntityFetcher.fetchCompletedActivityInstances(any()))
      .thenReturn(engineResultList);
    configurationService.setEngineImportActivityInstanceMaxPageSize(1);

    // when
    final boolean result = underTest.importNextEnginePage();

    // then
    assertThat(result, is(true));
  }
}