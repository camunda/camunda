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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.util.NoBeansTest;
import io.camunda.tasklist.util.apps.nobeans.TestApplicationWithNoBeans;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.os.ImportBatchOpenSearch;
import io.camunda.tasklist.zeebeimport.os.ImportJobOpenSearch;
import io.camunda.webapps.schema.entities.ImportPositionEntity;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.opensearch.client.opensearch.OpenSearchClient;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@SpringBootTest(
    classes = {
      TestApplicationWithNoBeans.class,
      ImportJobOpenSearch.class,
      ImportListenerTestOpenSearch.TestImportListener.class
    })
@Conditional(OpenSearchCondition.class)
public class ImportListenerTestOpenSearch extends NoBeansTest {

  @MockBean private ImportBatchProcessorFactory importBatchProcessorFactory;

  @MockBean private ImportBatchProcessor importBatchProcessor;

  @MockBean private ImportPositionHolder importPositionHolder;

  @MockBean
  @Qualifier("tasklistZeebeOsClient")
  private OpenSearchClient zeebeOsClient;

  @MockBean private RecordsReaderHolder recordsReaderHolder;

  @MockBean private TasklistProperties tasklistProperties;

  @Autowired private BeanFactory beanFactory;

  @Autowired private TestImportListener importListener;

  @BeforeEach
  public void before() {
    importListener.cancel();
  }

  @Test
  public void testFinished() throws Exception {
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
    } catch (PersistenceException e) {
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
  public void testFailed() throws Exception {
    final ImportBatchOpenSearch importBatchElasticSearch =
        new ImportBatchOpenSearch(1, ImportValueType.PROCESS_INSTANCE, new ArrayList<>(), null);
    final ImportPositionEntity previousPosition =
        new ImportPositionEntity().setAliasName("alias").setPartitionId(1).setPosition(0);
    final ImportJob importJob =
        beanFactory.getBean(ImportJobOpenSearch.class, importBatchElasticSearch, previousPosition);
    // mock import methods
    try {
      doThrow(new PersistenceException())
          .when(importBatchProcessor)
          .performImport(importBatchElasticSearch);
    } catch (PersistenceException e) {
      // ignore
    }

    // when the job is executed
    importJob.call();

    // then
    assertTrue(importListener.isFailedCalled());
    assertFalse(importListener.isFinishedCalled());
    assertEquals(importListener.getImportBatch(), importBatchElasticSearch);
  }

  @Component
  static class TestImportListener implements ImportListener {

    private boolean finishedCalled = false;
    private boolean failedCalled = false;
    private ImportBatch importBatchElasticSearch;

    @Override
    public void finished(ImportBatch importBatchElasticSearch) {
      finishedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    @Override
    public void failed(ImportBatch importBatchElasticSearch) {
      failedCalled = true;
      this.importBatchElasticSearch = importBatchElasticSearch;
    }

    public boolean isFinishedCalled() {
      return finishedCalled;
    }

    public boolean isFailedCalled() {
      return failedCalled;
    }

    public ImportBatch getImportBatch() {
      return importBatchElasticSearch;
    }

    public void cancel() {
      finishedCalled = false;
      failedCalled = false;
      importBatchElasticSearch = null;
    }
  }
}
