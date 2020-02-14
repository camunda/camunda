/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.rest.engine.EngineContext;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.EngineImportIndexHandlerRegistry;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.BeanFactory;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class CompletedActivityInstanceEngineImportMediatorTest extends TimestampBasedImportMediatorTest {

  @Mock
  private EngineImportIndexHandlerRegistry importIndexHandlerRegistry;

  @Mock
  private BeanFactory beanFactory;

  @Mock
  private EngineContext engineContext;

  @Mock
  private CompletedActivityInstanceFetcher engineEntityFetcher;

  private ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();

  @BeforeEach
  public void init() {
    when(beanFactory.getBean(CompletedActivityInstanceFetcher.class, engineContext))
      .thenReturn(engineEntityFetcher);

    this.underTest = new CompletedActivityInstanceEngineImportMediator(engineContext);
    ((CompletedActivityInstanceEngineImportMediator) this.underTest).setImportIndexHandlerRegistry(importIndexHandlerRegistry);
    this.underTest.beanFactory = beanFactory;
    this.underTest.configurationService = configurationService;
    this.underTest.init();

    super.init();
  }

  @Test
  public void testImportNextEnginePage_returnsFalse() {
    // when
    final boolean result = underTest.importNextPage();

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
    final boolean result = underTest.importNextPage();

    // then
    assertThat(result, is(true));
  }

}
