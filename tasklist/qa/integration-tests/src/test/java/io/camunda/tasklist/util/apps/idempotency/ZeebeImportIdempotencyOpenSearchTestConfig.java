/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.util.apps.idempotency;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.v870.processors.os.OpenSearchBulkProcessor;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock OpenSearchBulkProcessor, so that it persists the data successfully, but throw an
 * exception after that. This will cause the data to be imported twice.
 */
@Configuration
@Conditional(OpenSearchCondition.class)
public class ZeebeImportIdempotencyOpenSearchTestConfig {

  @Bean
  @Primary
  public CustomOpenSearchBulkProcessor openSearchBulkProcessor() {
    return new CustomOpenSearchBulkProcessor();
  }

  public static class CustomOpenSearchBulkProcessor extends OpenSearchBulkProcessor {

    private final Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(final ImportBatch importBatchOpenSearch) throws PersistenceException {
      super.performImport(importBatchOpenSearch);
      final ImportValueType importValueType = importBatchOpenSearch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(
            String.format(
                "Fake exception when saving data of type %s to OpenSearch", importValueType));
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }
}
