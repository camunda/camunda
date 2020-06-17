/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.importing.engine;

import org.camunda.optimize.service.importing.EngineImportMediator;
import org.camunda.optimize.service.util.ImportJobExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class EngineImportSchedulerTest {

  @Mock
  private EngineImportMediator mockedImportMediator;

  @Mock
  private ImportJobExecutor mockedImportJobExecutor;


  private EngineImportScheduler underTest;

  @BeforeEach
  public void before() {
    underTest = new EngineImportScheduler(
      Collections.singletonList(mockedImportMediator),
      "camundabpm"
    );
  }

  @Test
  public void isImportingIsTrueWhenImporting() {
    // given
    Mockito.when(mockedImportMediator.canImport()).thenReturn(true);
    Mockito.lenient().when(mockedImportMediator.runImport()).thenReturn(CompletableFuture.completedFuture(null));

    // when
    underTest.runImportRound();

    // then
    assertThat(underTest.isImporting(), is(true));
  }


  @Test
  public void isImportingIsTrueWhenActiveImportJob() {
    // given
    // it is indeed used but mockito reports this still as an unnecessary stubbing which it isn't, thus usage of lenient
    Mockito.lenient().when(mockedImportMediator.canImport()).thenReturn(true);
    Mockito.lenient().when(mockedImportMediator.runImport()).thenReturn(CompletableFuture.completedFuture(null));

    // first round of importing
    underTest.runImportRound();

    Mockito.when(mockedImportMediator.canImport()).thenReturn(false);
    Mockito.when(mockedImportMediator.getImportJobExecutor()).thenReturn(mockedImportJobExecutor);
    Mockito.when(mockedImportJobExecutor.isActive()).thenReturn(true);

    // when
    underTest.runImportRound();

    // then
    assertThat(underTest.isImporting(), is(true));
  }

  @Test
  public void isImportingIsFalseWhenNoActiveImportJob() {
    // given
    // it is indeed used but mockito reports this still as an unnecessary stubbing which it isn't, thus usage of lenient
    Mockito.lenient().when(mockedImportMediator.canImport()).thenReturn(true);
    Mockito.lenient().when(mockedImportMediator.runImport()).thenReturn(CompletableFuture.completedFuture(null));

    // first round of importing
    underTest.runImportRound();

    Mockito.when(mockedImportMediator.canImport()).thenReturn(false);
    Mockito.when(mockedImportMediator.getImportJobExecutor()).thenReturn(mockedImportJobExecutor);
    Mockito.when(mockedImportJobExecutor.isActive()).thenReturn(false);

    // when
    underTest.runImportRound();

    // then
    assertThat(underTest.isImporting(), is(false));
  }

}