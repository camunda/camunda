/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist.util.apps.idempotency;

import io.zeebe.tasklist.exceptions.PersistenceException;
import io.zeebe.tasklist.zeebe.ImportValueType;
import io.zeebe.tasklist.zeebeimport.ImportBatch;
import io.zeebe.tasklist.zeebeimport.v100.processors.ElasticsearchBulkProcessor;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it persists the data successfully, but throw an
 * exception after that. This will cause the data to be imported twice.
 */
@Configuration
public class ZeebeImportIdempotencyTestConfig {

  @Bean("io.zeebe.tasklist.zeebeimport.v25.processors.ElasticsearchBulkProcessor")
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {

    private Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(ImportBatch importBatch) throws PersistenceException {
      super.performImport(importBatch);
      final ImportValueType importValueType = importBatch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(
            String.format(
                "Fake exception when saving data of type %s to Elasticsearch", importValueType));
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }
}
