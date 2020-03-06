/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.importing.engine.handler.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.service.ImportService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
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

  private OffsetDateTime importTimestamp;

  protected void init() {
    this.underTest.importIndexHandler = importIndexHandler;
    this.underTest.importService = importService;
    Mockito.lenient().doAnswer(invocation -> {
      final Runnable runnable = invocation.getArgument(1);
      runnable.run();
      return null;
    }).when(importService).executeImport(any(), any(Runnable.class));
    importTimestamp = OffsetDateTime.now();
    LocalDateUtil.setCurrentTime(importTimestamp);
  }

  @Test
  public void testImportNextEnginePageTimestampBased_noEntitiesOfLastTimestamp() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importService, times(0)).executeImport(any(), any());
  }

  @Test
  public void testImportNextEnginePageTimestampBased_withEntitiesOfLastTimestamp() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    entitiesLastTimestamp.add(new HistoricActivityInstanceEngineDto());

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importService, times(1)).executeImport(any(), any());
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importIndexHandler, times(0)).updateTimestampOfLastEntity(any());
  }

  @Test
  public void testImportNextEnginePageTimestampBased_timestampNeedsToBeSetTrue() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();
    entitiesNextPage.add(new HistoricActivityInstanceEngineDto());

    // when
    runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importService, times(1)).executeImport(any(), any());
    verify(importIndexHandler, times(1)).updatePendingTimestampOfLastEntity(any());
    verify(importIndexHandler, times(1)).updateTimestampOfLastEntity(any());
  }

  @Test
  public void testImportNextEnginePageTimestampBased_returnsFalse() {
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    final boolean result = runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    assertThat(result).isFalse();
  }

  @Test
  public void testImportNextEnginePageTimestampBased_returnsTrue() {
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();
    entitiesNextPage.add(new HistoricActivityInstanceEngineDto());

    // when
    final boolean result = runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    assertThat(result).isTrue();
  }

  private boolean runAndFinishImport(final List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp,
                                     final List<HistoricActivityInstanceEngineDto> entitiesNextPage) {
    final CompletableFuture<Void> importCompleteFuture = new CompletableFuture<>();
    final boolean result = underTest.importNextEnginePageTimestampBased(
      entitiesLastTimestamp,
      entitiesNextPage,
      1,
      () -> importCompleteFuture.complete(null)
    );
    assertThat(importCompleteFuture).isCompleted();
    return result;
  }
}
