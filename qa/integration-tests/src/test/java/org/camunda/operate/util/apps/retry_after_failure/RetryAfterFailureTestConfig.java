/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.apps.retry_after_failure;

import java.util.HashSet;
import java.util.Set;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.v26.processors.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.ImportBatch;
import org.camunda.operate.zeebe.ImportValueType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it throw an exception with the 2st run and persist the data only with the second run.
 */
@Configuration
public class RetryAfterFailureTestConfig {

  @Bean("org.camunda.operate.zeebeimport.v25.processors.ElasticsearchBulkProcessor")
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {

    private Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(ImportBatch importBatch) throws PersistenceException {
      ImportValueType importValueType = importBatch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(String.format("Fake exception when saving data of type %s to Elasticsearch", importValueType));
      } else {
        super.performImport(importBatch);
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }

}
