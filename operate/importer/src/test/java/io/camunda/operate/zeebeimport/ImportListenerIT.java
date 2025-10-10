/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.zeebeimport;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertFalse;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.entities.HitEntity;
import io.camunda.operate.entities.meta.ImportPositionEntity;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.store.ImportStore;
import io.camunda.operate.store.ZeebeStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchImportStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchZeebeStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.util.NoBeansIT;
import io.camunda.operate.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.processors.ImportBulkProcessor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ImportJob.class,
      ImportListenerIT.TestImportListener.class,
      ImportStore.class,
      ElasticsearchImportStore.class,
      RetryElasticsearchClient.class,
      ElasticsearchTaskStore.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      DatabaseInfo.class,
      OperateProperties.class,
      ImportJob.class,
      ImportPositionIndex.class,
      Metrics.class,
      ZeebeStore.class,
      ElasticsearchZeebeStore.class
    })
public class ImportListenerIT extends NoBeansIT {

  @MockBean private ImportBatchProcessorFactory importBatchProcessorFactory;
  @MockBean private ImportBulkProcessor elasticsearchBulkProcessor;
  @MockBean private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("zeebeEsClient")
  private RestHighLevelClient zeebeEsClient;

  @MockBean private RecordsReaderHolder recordsReaderHolder;
  @Autowired private OperateProperties operateProperties;
  @Autowired private BeanFactory beanFactory;
  @Autowired private TestImportListener importListener;

  @Before
  public void before() {
    importListener.cancel();
  }

  @Test
  public void testFinished() {
    final ImportBatch importBatch =
        new ImportBatch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), "some_name");
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity()
            .setAliasName("alias")
            .setPartitionId(1)
            .setPosition(0)
            .setSequence(0L);
    final ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, previousPosition);

    // mock import methods
    try {
      when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
          .thenReturn(elasticsearchBulkProcessor);
      doNothing().when(elasticsearchBulkProcessor).performImport(importBatch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFinishedCalled());
    assertFalse(importListener.isFailedCalled());
    assertEquals(importListener.getImportBatch(), importBatch);
  }

  @Test
  public void testFailed() {
    final ImportBatch importBatch =
        new ImportBatch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity()
            .setAliasName("alias")
            .setPartitionId(1)
            .setPosition(0)
            .setSequence(0L);
    final ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(elasticsearchBulkProcessor)
          .performImport(importBatch);
    } catch (final PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatch);
  }

  @Test
  public void testBatchSizeLimitingSplitsBatchesCorrectly() throws Exception {
    // given a small configured batch size (e.g., 1000 bytes)
    operateProperties.setImporterMaxBatchSizeBytes(1000L);

    // with 5 hits of ~450 bytes
    final List<HitEntity> hits =
        IntStream.range(0, 5)
            .mapToObj(
                i -> {
                  final HitEntity hit = new HitEntity();
                  hit.setIndex("test_index");
                  hit.setSourceAsString(
                      "{ \"fieldName\": \"" + "x".repeat(400) + "\"}"); // >400 && <500 bytes
                  return hit;
                })
            .collect(Collectors.toList());

    final ImportBatch importBatch =
        new ImportBatch(1, ImportValueType.PROCESS_INSTANCE, hits, "test_index");
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity()
            .setAliasName("alias")
            .setPartitionId(1)
            .setPosition(0)
            .setSequence(0L);
    final ImportJob importJob = beanFactory.getBean(ImportJob.class, importBatch, previousPosition);

    final ImportBulkProcessor processorMock = elasticsearchBulkProcessor;

    when(importBatchProcessorFactory.getImportBatchProcessor(anyString()))
        .thenReturn(processorMock);
    doNothing().when(processorMock).performImport(any(ImportBatch.class));

    // when
    importJob.call();

    // then the import is made the expected number of times (3 batches - 2/2/1)
    final ArgumentCaptor<ImportBatch> captor = ArgumentCaptor.forClass(ImportBatch.class);
    verify(processorMock, times(3)).performImport(captor.capture());
    final List<ImportBatch> batches = captor.getAllValues();
    final List<Integer> batchSizes =
        batches.stream().map(b -> b.getHits().size()).collect(Collectors.toList());
    assertEquals(List.of(2, 2, 1), batchSizes);
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatch;

    @Override
    public void finished(final ImportBatch importBatch) {
      finishedCalled = true;
      this.importBatch = importBatch;
    }

    @Override
    public void failed(final ImportBatch importBatch) {
      failedCalled = true;
      this.importBatch = importBatch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatch = null;
    }
  }
}
