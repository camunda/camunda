/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.importing;

import org.camunda.optimize.dto.engine.HistoricActivityInstanceEngineDto;
import org.camunda.optimize.service.importing.engine.fetcher.instance.CompletedActivityInstanceFetcher;
import org.camunda.optimize.service.importing.engine.handler.CompletedActivityInstanceImportIndexHandler;
import org.camunda.optimize.service.importing.engine.mediator.CompletedActivityInstanceEngineImportMediator;
import org.camunda.optimize.service.importing.engine.service.CompletedActivityInstanceImportService;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.camunda.optimize.service.util.BackoffCalculator;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TimestampBasedImportMediatorTest {

  // we test using one particular implementation
  protected TimestampBasedImportMediator
    <CompletedActivityInstanceImportIndexHandler, HistoricActivityInstanceEngineDto> underTest;

  @Mock
  protected CompletedActivityInstanceImportIndexHandler importIndexHandler;

  @Mock
  protected CompletedActivityInstanceImportService importService;

  @Captor
  private ArgumentCaptor<List<HistoricActivityInstanceEngineDto>> importEntitiesCaptor;

  @Mock
  private CompletedActivityInstanceFetcher engineEntityFetcher;

  @Mock
  private BackoffCalculator idleBackoffCalculator;

  private ConfigurationService configurationService = ConfigurationServiceBuilder.createDefaultConfiguration();

  @BeforeEach
  public void init() {
    this.underTest = new CompletedActivityInstanceEngineImportMediator(
      importIndexHandler,
      engineEntityFetcher,
      importService,
      configurationService,
      idleBackoffCalculator
    );

    Mockito.lenient().doAnswer(invocation -> {
      final Runnable runnable = invocation.getArgument(1);
      runnable.run();
      return null;
    }).when(importService).executeImport(any(), any(Runnable.class));
    LocalDateUtil.setCurrentTime(OffsetDateTime.now());
    Mockito.lenient().when(importIndexHandler.getEngineAlias()).thenReturn("camunda-bpm");
  }

  @Test
  public void testImportNextEnginePage_returnsFalse() {
    // when
    final boolean result = underTest.importNextPage(() -> {
    });

    // then
    assertThat(result).isFalse();
  }

  @Test
  public void testImportNextEnginePage_returnsTrue() {
    // given
    List<HistoricActivityInstanceEngineDto> engineResultList = new ArrayList<>();
    engineResultList.add(createHistoricActivityInstance(OffsetDateTime.now()));
    when(engineEntityFetcher.fetchCompletedActivityInstances(any())).thenReturn(engineResultList);
    configurationService.setEngineImportActivityInstanceMaxPageSize(1);

    // when
    final boolean result = underTest.importNextPage(() -> {
    });

    // then
    assertThat(result).isTrue();
  }

  @Test
  public void testImportNextEnginePageTimestampBased_noNewDataForSameTimestamp_butNewNextPage() {
    // given
    final OffsetDateTime otherEntityTimestamp = OffsetDateTime.now();
    final OffsetDateTime firstNewPageEntityTimestamp = otherEntityTimestamp.plus(500, ChronoUnit.MILLIS);
    final OffsetDateTime lastEntityTimestamp = OffsetDateTime.now().plusSeconds(1);
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp1 = new ArrayList<>();
    entitiesLastTimestamp1.add(createHistoricActivityInstance(otherEntityTimestamp));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage1 = new ArrayList<>();
    entitiesNextPage1.add(createHistoricActivityInstance(firstNewPageEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    runAndFinishImport(entitiesLastTimestamp1, entitiesNextPage1);

    // clearing invocations done from given setup
    Mockito.clearInvocations(importService, importIndexHandler);

    // when
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp2 = new ArrayList<>();
    entitiesLastTimestamp2.add(entitiesNextPage1.get(1));
    entitiesLastTimestamp2.add(entitiesNextPage1.get(2));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage2 = new ArrayList<>();
    entitiesNextPage2.add(createHistoricActivityInstance(OffsetDateTime.now().plusSeconds(2)));

    runAndFinishImport(entitiesLastTimestamp2, entitiesNextPage2);

    // then
    // import is executed as there is new data on the next page
    verify(importService, times(1)).executeImport(importEntitiesCaptor.capture(), any());
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importIndexHandler, times(1)).updateTimestampOfLastEntity(any());
    // but the import batch only contains the next page entities as the count of same timestamp entities is the same
    assertThat(importEntitiesCaptor.getValue()).isEqualTo(entitiesNextPage2);
    assertThat(underTest.countOfImportedEntitiesWithLastEntityTimestamp).isEqualTo(1);
  }

  @Test
  public void testImportNextEnginePageTimestampBased_newDataForSameTimestamp_emptyNextPage() {
    // given
    final OffsetDateTime otherEntityTimestamp = OffsetDateTime.now();
    final OffsetDateTime firstNewPageEntityTimestamp = otherEntityTimestamp.plus(500, ChronoUnit.MILLIS);
    final OffsetDateTime lastEntityTimestamp = OffsetDateTime.now().plusSeconds(1);
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp1 = new ArrayList<>();
    entitiesLastTimestamp1.add(createHistoricActivityInstance(otherEntityTimestamp));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage1 = new ArrayList<>();
    entitiesNextPage1.add(createHistoricActivityInstance(firstNewPageEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    runAndFinishImport(entitiesLastTimestamp1, entitiesNextPage1);

    // clearing invocations done from given setup
    Mockito.clearInvocations(importService, importIndexHandler);

    // when
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp2 = new ArrayList<>();
    entitiesLastTimestamp2.add(entitiesNextPage1.get(1));
    entitiesLastTimestamp2.add(entitiesNextPage1.get(2));
    entitiesLastTimestamp2.add(createHistoricActivityInstance(lastEntityTimestamp));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage2 = new ArrayList<>();

    runAndFinishImport(entitiesLastTimestamp2, entitiesNextPage2);

    // then
    // import is executed for second invocation
    verify(importService, times(1)).executeImport(importEntitiesCaptor.capture(), any());
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importIndexHandler, times(0)).updateTimestampOfLastEntity(any());
    // and all the same timestamp entities are present in the import batch
    assertThat(importEntitiesCaptor.getValue()).isEqualTo(entitiesLastTimestamp2);
    assertThat(underTest.countOfImportedEntitiesWithLastEntityTimestamp).isEqualTo(3);
  }

  @Test
  public void testRunImport_verifyCorrectBackoff() {
    // given
    final OffsetDateTime now = OffsetDateTime.now();
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();
    entitiesLastTimestamp.add(createHistoricActivityInstance(now));

    Mockito.when(underTest.getEntitiesLastTimestamp()).thenReturn(entitiesLastTimestamp);
    Mockito.when(underTest.getEntitiesNextPage()).thenReturn(new ArrayList<>());

    // when running import for empty page
    underTest.runImport();

    // then
    verify(idleBackoffCalculator, atLeastOnce()).isMaximumBackoffReached();
    verify(idleBackoffCalculator, atLeastOnce()).calculateSleepTime();

    // clearing invocations done from given setup
    Mockito.clearInvocations(idleBackoffCalculator);

    // given
    final OffsetDateTime entityTimestamp = OffsetDateTime.now().plusSeconds(1);
    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    for (int i = 0; i < configurationService.getEngineImportActivityInstanceMaxPageSize(); i++) {
      entitiesNextPage.add(createHistoricActivityInstance(entityTimestamp));
    }

    Mockito.when(underTest.getEntitiesNextPage()).thenReturn(entitiesNextPage);

    // when running import with a full next page
    underTest.runImport();

    // then
    verify(idleBackoffCalculator, atLeastOnce()).resetBackoff();
  }

  @Test
  public void testImportNextEnginePageTimestampBased_noNewDataForSameTimestamp_emptyNextPage() {
    // given
    final OffsetDateTime otherEntityTimestamp = OffsetDateTime.now();
    final OffsetDateTime firstNewPageEntityTimestamp = otherEntityTimestamp.plus(500, ChronoUnit.MILLIS);
    final OffsetDateTime lastEntityTimestamp = OffsetDateTime.now().plusSeconds(1);
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp1 = new ArrayList<>();
    entitiesLastTimestamp1.add(createHistoricActivityInstance(otherEntityTimestamp));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage1 = new ArrayList<>();
    entitiesNextPage1.add(createHistoricActivityInstance(firstNewPageEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    entitiesNextPage1.add(createHistoricActivityInstance(lastEntityTimestamp));
    runAndFinishImport(entitiesLastTimestamp1, entitiesNextPage1);

    // clearing invocations done from given setup
    Mockito.clearInvocations(importService, importIndexHandler);

    // when
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp2 = new ArrayList<>();
    entitiesLastTimestamp2.add(entitiesNextPage1.get(1));
    entitiesLastTimestamp2.add(entitiesNextPage1.get(2));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage2 = new ArrayList<>();

    runAndFinishImport(entitiesLastTimestamp2, entitiesNextPage2);

    // then
    // import is not executed for second invocation, as there is nothing new
    verify(importService, times(0)).executeImport(any(), any());
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importIndexHandler, times(0)).updateTimestampOfLastEntity(any());
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
    entitiesLastTimestamp.add(createHistoricActivityInstance(OffsetDateTime.now()));

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();

    // when
    runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importService, times(1)).executeImport(any(), any());
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    verify(importIndexHandler, times(0)).updateTimestampOfLastEntity(any());
    assertThat(underTest.countOfImportedEntitiesWithLastEntityTimestamp).isEqualTo(1);
  }

  @Test
  public void testImportNextEnginePageTimestampBased_timestampNeedsToBeSetTrue() {
    // given
    List<HistoricActivityInstanceEngineDto> entitiesLastTimestamp = new ArrayList<>();

    List<HistoricActivityInstanceEngineDto> entitiesNextPage = new ArrayList<>();
    entitiesNextPage.add(createHistoricActivityInstance(OffsetDateTime.now()));

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
    entitiesNextPage.add(createHistoricActivityInstance(OffsetDateTime.now()));

    // when
    final boolean result = runAndFinishImport(entitiesLastTimestamp, entitiesNextPage);

    // then
    verify(importIndexHandler, times(1)).updateLastImportExecutionTimestamp();
    assertThat(result).isTrue();
  }

  private HistoricActivityInstanceEngineDto createHistoricActivityInstance(final OffsetDateTime endTime) {
    final HistoricActivityInstanceEngineDto historicActivityInstanceEngineDto = new HistoricActivityInstanceEngineDto();
    historicActivityInstanceEngineDto.setEndTime(endTime);
    return historicActivityInstanceEngineDto;
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
