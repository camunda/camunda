/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist.util.apps.retry_after_failure;

import io.camunda.tasklist.exceptions.PersistenceException;
import io.camunda.tasklist.zeebe.ImportValueType;
import io.camunda.tasklist.zeebeimport.ImportBatch;
import io.camunda.tasklist.zeebeimport.v840.processors.os.OpenSearchBulkProcessor;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock OpenSearchBulkProcessor, so that it throw an exception with the 2st run and persist
 * the data only with the second run.
 */
@Configuration
public class RetryAfterFailureTestOpenSearchConfig {

  @Bean("io.camunda.tasklist.zeebeimport.v830.processors.os.OpenSearchBulkProcessor")
  @Primary
  public CustomOpenSearchBulkProcessor openSearchBulkProcessor() {
    return new CustomOpenSearchBulkProcessor();
  }

  public static class CustomOpenSearchBulkProcessor extends OpenSearchBulkProcessor {

    private Set<ImportValueType> alreadyFailedTypes = new HashSet<>();

    @Override
    public void performImport(ImportBatch importBatchElasticSearch) throws PersistenceException {
      final ImportValueType importValueType = importBatchElasticSearch.getImportValueType();
      if (!alreadyFailedTypes.contains(importValueType)) {
        alreadyFailedTypes.add(importValueType);
        throw new PersistenceException(
            String.format(
                "Fake exception when saving data of type %s to OpenSearch", importValueType));
      } else {
        super.performImport(importBatchElasticSearch);
      }
    }

    public void cancelAttempts() {
      alreadyFailedTypes.clear();
    }
  }
}
