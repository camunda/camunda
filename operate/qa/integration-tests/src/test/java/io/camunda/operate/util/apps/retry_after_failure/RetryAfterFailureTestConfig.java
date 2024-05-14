/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.apps.retry_after_failure;

import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebe.ImportValueType;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebeimport.processors.ImportBulkProcessor;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it throw an exception with the 2st run and persist
 * the data only with the second run.
 */
@Configuration
public class RetryAfterFailureTestConfig {

  @Bean("io.camunda.operate.zeebeimport.processors.ElasticsearchBulkProcessor")
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ImportBulkProcessor {

    private final Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(final ImportBatch importBatch) throws PersistenceException {
      final ImportValueType importValueType = importBatch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(
            String.format(
                "Fake exception when saving data of type %s to Elasticsearch", importValueType));
      } else {
        super.performImport(importBatch);
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }
}
