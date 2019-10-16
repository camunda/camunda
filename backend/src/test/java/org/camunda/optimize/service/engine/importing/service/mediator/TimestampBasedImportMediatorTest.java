/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing.service.mediator;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.engine.importing.index.handler.impl.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.engine.importing.service.ImportService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.core.Is.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public abstract class TimestampBasedImportMediatorTest {

  protected TimestampBasedImportMediator<CompletedActivityInstanceImportIndexHandler,
    HistoricActivityInstanceEngineDto> underTest;

  @Mock
  protected CompletedActivityInstanceImportIndexHandler importIndexHandler;

  @Mock
  protected ImportService<HistoricActivityInstanceEngineDto> importService;

  @Captor
  protected ArgumentCaptor<Runnable> callbackLambdaCaptor;

  protected void init() {

    this.underTest.importIndexHandler = importIndexHandler;
    this.underTest.importService = importService;
  }

  @Test
  public void testImportNextEnginePageTimestampBased_noEntitiesOfLastTimestamp() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1
    );

    // then
    verify(importService, times(0)).executeImport(any(), any());
  }

  @Test
  public void testImportNextEnginePageTimestampBased_withEntitiesOfLastTimestamp() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    entitiesLastTimestamp.add(new HistoricActivityInstanceEngineDto());

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1
    );

    // then
    verify(importService, times(1)).executeImport(any());
    verify(importIndexHandler, times(0)).updateTimestampOfLastEntity(any());
  }

  @Test
  public void testImportNextEnginePageTimestampBased_timestampNeedsToBeSetTrue() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();
    entitiesNextPage.add(new HistoricActivityInstanceEngineDto());

    // when
    underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1
    );

    // then
    verify(importService, times(1)).executeImport(any(), any());
    verify(importIndexHandler, times(1)).updatePendingTimestampOfLastEntity(any());
    verify(importService).executeImport(any(), callbackLambdaCaptor.capture());

    Runnable lambda = callbackLambdaCaptor.getValue();
    lambda.run();

    verify(importIndexHandler, times(1)).updateTimestampOfLastEntity(any());
  }


  @Test
  public void testImportNextEnginePageTimestampBased_returnsFalse() {
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    final boolean result = underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1
    );

    // then
    assertThat(result, is(false));
  }

  @Test
  public void testImportNextEnginePageTimestampBased_returnsTrue() {
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();
    entitiesNextPage.add(new HistoricActivityInstanceEngineDto());

    // when
    final boolean result = underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1
    );

    // then
    assertThat(result, is(true));
  }
}