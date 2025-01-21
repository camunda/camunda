/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.idempotency;

import io.camunda.tasklist.data.conditionals.ElasticSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.v870.processors.es.BulkProcessorElasticSearch;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it persists the data successfully, but throw an
 * exception after that. This will cause the data to be imported twice.
 */
@Configuration
@Conditional(ElasticSearchCondition.class)
public class ZeebeImportIdempotencyElasticSearchTestConfig {

  @Bean
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends BulkProcessorElasticSearch {

    private final Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(final ImportBatch importBatchElasticSearch)
        throws PersistenceException {
      super.performImport(importBatchElasticSearch);
      final ImportValueType importValueType = importBatchElasticSearch.getImportValueType();
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
