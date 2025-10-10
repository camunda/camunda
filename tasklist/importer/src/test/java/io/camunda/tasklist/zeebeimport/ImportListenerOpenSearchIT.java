/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.zeebeimport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.entities.meta.ImportPositionEntity;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.NoBeansTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.os.ImportBatchOpenSearch;
import io.camunda.tasklist.zeebeimport.os.ImportJobOpenSearch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mockito.ArgumentCaptor;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.opensearch.client.opensearch.core.search.Hit;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ImportJobOpenSearch.class,
      JacksonConfig.class,
      TasklistProperties.class,
      ImportListenerOpenSearchIT.TestImportListener.class
    },
    properties = "camunda.tasklist.database=opensearch")
@EnabledIfSystemProperty(named = "camunda.tasklist.database", matches = "opensearch")
public class ImportListenerOpenSearchIT extends NoBeansTest {

  @MockBean private ImportBatchProcessorFactory importBatchProcessorFactory;

  @MockBean private ImportBatchProcessor importBatchProcessor;

  @MockBean private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("tasklistZeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @MockBean private RecordsReaderHolder recordsReaderHolder;

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TestImportListener importListener;

  @BeforeEach
  public void before() {
    importListener.cancel();
  }

  @Test
  public void testFinished() throws Exception {
    final Hit mockedSearchHit = mock(Hit.class);
    final String indexName = "some_name-8.7.0_";
    when(mockedSearchHit.index()).thenReturn(indexName);
    when(mockedSearchHit.source()).thenReturn(Map.of("position", 12345, "sequence", 67890));
    final ImportBatch importBatchOpenSearch =
        new ImportBatchOpenSearch(
            1, ImportValueType.PROCESS_INSTANCE, Arrays.asList(mockedSearchHit), indexName);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(importBatchProcessor);
      doNothing().when(importBatchProcessor).performImport(importBatchOpenSearch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFinishedCalled());
    assertFalse(importListener.isFailedCalled());
    assertEquals(importListener.getImportBatch(), importBatchOpenSearch);
  }

  @Test
  public void testEmptyBatch() throws Exception {
    final ImportBatch importBatchOpenSearch =
        new ImportBatchOpenSearch(
            1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), "some_name");
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(importBatchProcessor);
      doNothing().when(importBatchProcessor).performImport(importBatchOpenSearch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertFalse(importListener.isFinishedCalled()); // empty batches do not trigger finished
    assertFalse(importListener.isFailedCalled());
    assertNull(importListener.getImportBatch());
  }

  @Test
  public void testFailed() throws Exception {
    final Hit mockedSearchHit = mock(Hit.class);
    final String indexName = "some_name-8.7.0_";
    when(mockedSearchHit.index()).thenReturn(indexName);
    when(mockedSearchHit.source()).thenReturn(Map.of("position", 12345, "sequence", 67890));
    final ImportBatchOpenSearch importBatchOpenSearch =
        new ImportBatchOpenSearch(
            1, ImportValueType.PROCESS_INSTANCE, Arrays.asList(mockedSearchHit), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(importBatchProcessor)
          .performImport(importBatchOpenSearch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatchOpenSearch);
  }

  @Test
  public void testFailedEmptyBatch() throws Exception {
    final ImportBatchOpenSearch importBatchOpenSearch =
        new ImportBatchOpenSearch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(importBatchProcessor)
          .performImport(importBatchOpenSearch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertFalse(importListener.isFailedCalled()); // empty batches do not trigger finished
    assertFalse(importListener.isFinishedCalled());
    assertNull(importListener.getImportBatch());
  }

  @Test
  public void testBatchSizeLimitingSplitsBatchesCorrectly() throws Exception {
    // given a small configured batch size (e.g., 150 bytes)
    tasklistProperties.getImporter().setMaxBatchSizeBytes(150L);
    final String indexName = "some_name-8.7.0_";
    // with 5 hits of ~50 bytes (this is the size of the mocked hit)
    final List<Hit> mockedHits =
        IntStream.range(0, 5)
            .mapToObj(
                i -> {
                  final Hit hit = mock(Hit.class);
                  when(hit.index()).thenReturn(indexName);
                  when(hit.source()).thenReturn(Map.of("position", 12345, "sequence", 67890));
                  return hit;
                })
            .toList();

    final ImportBatch importBatchOpenSearch =
        new ImportBatchOpenSearch(1, ImportValueType.PROCESS_INSTANCE, mockedHits, indexName);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchOpenSearch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(importBatchProcessor);
      doNothing().when(importBatchProcessor).performImport(importBatchOpenSearch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then the import is made the expected number of times (3 batches - 2/2/1)
    final ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
    verify(importBatchProcessor, times(3)).performImport(captor.capture());
    final List<ImportBatch> batches = captor.getAllValues();
    final List<Integer> batchSizes =
        batches.stream().map(b -> b.getHits().size()).collect(Collectors.toList());
    assertEquals(List.of(2, 2, 1), batchSizes);
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatchOpenSearch;

    @Override
    public void finished(final ImportBatch importBatchOpenSearch) {
      finishedCalled = true;
      this.importBatchOpenSearch = importBatchOpenSearch;
    }

    @Override
    public void failed(final ImportBatch importBatchOpenSearch) {
      failedCalled = true;
      this.importBatchOpenSearch = importBatchOpenSearch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatchOpenSearch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatchOpenSearch = null;
    }
  }
}
