/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util.apps.idempotency;

import java.util.List;
import org.camunda.operate.exceptions.PersistenceException;
import org.camunda.operate.zeebeimport.ElasticsearchBulkProcessor;
import org.camunda.operate.zeebeimport.record.RecordImpl;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Let's mock ElasticsearchBulkProcessor.
 */
@Configuration
public class ZeebeImportIdempotencyTestConfig {

  @Bean
  @Primary
  public CustomElasticsearchBulkProcessor elasticsearchBulkProcessor() {
    return new CustomElasticsearchBulkProcessor();
  }

  public static class CustomElasticsearchBulkProcessor extends ElasticsearchBulkProcessor {
    int attempts = 0;

    @Override
    public void persistZeebeRecords(List<? extends RecordImpl> zeebeRecords) throws PersistenceException {
      super.persistZeebeRecords(zeebeRecords);
      if (attempts < 1) {
        attempts++;
        throw new PersistenceException("Fake exception when saving data to Elasticsearch");
      }
    }

    public void cancelAttempts() {
      attempts = 0;
    }
  }

}
