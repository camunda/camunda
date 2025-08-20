/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static org.assertj.core.api.Assumptions.assumeThat;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.schema.util.SchemaTestHelper;
import io.camunda.operate.schema.util.SearchClientTestHelper;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchClientTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchClientTestHelper;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;

@SpringBootTest
@EnableConfigurationProperties
@DirtiesContext(classMode = ClassMode.AFTER_EACH_TEST_METHOD)
@ContextConfiguration(
    classes = {
      OperateProperties.class,
      DatabaseInfo.class,
      RetryElasticsearchClient.class,
      RichOpenSearchClient.class,
      ElasticsearchClientTestHelper.class,
      OpenSearchClientTestHelper.class
    })
public class AbstractSchemaIT {

  protected static final int REQUEST_RETRIES = 2;
  protected static final int RANDOM_PREFIX_LENGTH = 8;

  @Autowired protected SearchClientTestHelper clientTestHelper;

  @Autowired protected DatabaseInfo databaseInfo;

  @Autowired protected OperateProperties operateProperties;

  @Autowired protected SchemaTestHelper schemaHelper;

  @PostConstruct
  public void configureRetries() {
    clientTestHelper.setClientRetries(REQUEST_RETRIES);
  }

  @AfterEach
  public void dropSchema() {
    schemaHelper.dropSchema();
  }

  @BeforeEach
  public void initializeBeforeEachTest() {
    // randomize the schema prefix so multiple tests can run in parallel
    // on the same Elasticsearch instance
    final String indexPrefix =
        RandomStringUtils.random(RANDOM_PREFIX_LENGTH, true, false).toLowerCase();
    operateProperties.getElasticsearch().setIndexPrefix(indexPrefix);
    operateProperties.getOpensearch().setIndexPrefix(indexPrefix);
  }

  protected void assumeElasticsearchTest() {
    assumeThat(databaseInfo.isElasticsearchDb()).isTrue();
  }
}
