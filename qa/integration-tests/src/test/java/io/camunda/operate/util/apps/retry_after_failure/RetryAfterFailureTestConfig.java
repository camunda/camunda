/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.util.apps.retry_after_failure;

import java.util.HashSet;
import java.util.Set;
import io.camunda.operate.exceptions.PersistenceException;
import io.camunda.operate.zeebeimport.v8_3.processors.ElasticsearchBulkProcessor;
import io.camunda.operate.zeebeimport.ImportBatch;
import io.camunda.operate.zeebe.ImportValueType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor, so that it throw an exception with the 2st run and persist the data only with the second run.
 */
@Configuration
public class RetryAfterFailureTestConfig {

  @Bean("io.camunda.operate.zeebeimport.v8_3.processors.ElasticsearchBulkProcessor")
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
