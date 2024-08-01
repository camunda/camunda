/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.operate.JacksonConfig;
import io.camunda.operate.Metrics;
import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.connect.ElasticsearchClientProvider;
import io.camunda.operate.connect.OpensearchClientProvider;
import io.camunda.operate.connect.OperateDateTimeFormatter;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.elasticsearch.ElasticsearchSchemaManager;
import io.camunda.operate.schema.indices.ImportPositionIndex;
import io.camunda.operate.schema.opensearch.OpensearchSchemaManager;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.TestTemplate;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchSchemaTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchSchemaTestHelper;
import io.camunda.operate.store.ImportStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchImportStore;
import io.camunda.operate.store.elasticsearch.ElasticsearchTaskStore;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.OpensearchImportStore;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;

@ContextConfiguration(
    classes = {
      ImportPositionIndex.class,
      TestTemplate.class,
      ElasticsearchSchemaManager.class,
      ElasticsearchImportStore.class,
      OpensearchSchemaManager.class,
      OpensearchImportStore.class,
      OpenSearchSchemaTestHelper.class,
      ElasticsearchSchemaTestHelper.class,
      DatabaseInfo.class,
      OperateProperties.class,
      RetryElasticsearchClient.class,
      ElasticsearchClientProvider.class,
      OpensearchClientProvider.class,
      ElasticsearchTaskStore.class,
      JacksonConfig.class,
      OperateDateTimeFormatter.class,
      Metrics.class
    })
@SpringBootTest(properties = {"spring.profiles.active="})
public class ImportStoreIT extends AbstractSchemaIT {

  @Autowired public ImportStore importStore;

  @Autowired public SchemaManager schemaManager;

  @Autowired public ImportPositionIndex importPositionIndex;

  @Autowired public SchemaTestHelper schemaHelper;

  @MockBean public MeterRegistry meterRegistry;

  @BeforeEach
  public void createDefault() {
    schemaManager.createDefaults();
  }

  @AfterEach
  public void dropSchema() {
    schemaHelper.dropSchema();
  }

  @Test
  public void setAndGetConcurrencyMode() {
    schemaManager.createIndex(
        importPositionIndex, "/schema/elasticsearch/create/index/operate-import-position.json");

    assertThat(importStore.getConcurrencyMode()).isFalse();

    importStore.setConcurrencyMode(true);
    assertThat(importStore.getConcurrencyMode()).isTrue();

    importStore.setConcurrencyMode(false);
    assertThat(importStore.getConcurrencyMode()).isFalse();
  }
}
