/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.schema;

import static org.junit.jupiter.api.Assertions.fail;

import io.camunda.operate.conditions.DatabaseInfo;
import io.camunda.config.operate.OperateProperties;
import io.camunda.operate.schema.util.SearchClientTestHelper;
import io.camunda.operate.schema.util.elasticsearch.ElasticsearchClientTestHelper;
import io.camunda.operate.schema.util.opensearch.OpenSearchClientTestHelper;
import io.camunda.operate.store.elasticsearch.RetryElasticsearchClient;
import io.camunda.operate.store.opensearch.client.sync.RichOpenSearchClient;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.support.TestPropertySourceUtils;

@SpringBootTest
@EnableConfigurationProperties
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@ContextConfiguration(
    classes = {
      OperateProperties.class,
      DatabaseInfo.class,
      RetryElasticsearchClient.class,
      RichOpenSearchClient.class,
      ElasticsearchClientTestHelper.class,
      OpenSearchClientTestHelper.class
    },
    initializers = AbstractSchemaIT.Initializer.class)
public class AbstractSchemaIT {

  protected static final int REQUEST_RETRIES = 2;
  protected static final int RANDOM_PREFIX_LENGTH = 8;

  @Autowired protected SearchClientTestHelper clientTestHelper;

  @Autowired protected DatabaseInfo databaseInfo;

  @PostConstruct
  public void configureRetries() {
    clientTestHelper.setClientRetries(REQUEST_RETRIES);
  }

  protected void failIfOpensearch() {
    if (databaseInfo.isOpensearchDb()) {
      fail("Cannot proceed because this case is not yet implemented for OpenSearch");
    }
  }

  protected static class Initializer
      implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    @Override
    public void initialize(final ConfigurableApplicationContext applicationContext) {

      // randomize the schema prefix so multiple test classes can run in parallel
      // on the same Elasticsearch instance

      final String indexPrefix =
          RandomStringUtils.randomAlphabetic(RANDOM_PREFIX_LENGTH).toLowerCase();

      TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
          applicationContext,
          "camunda.operate.elasticsearch.indexPrefix=" + indexPrefix,
          "camunda.operate.opensearch.indexPrefix=" + indexPrefix);
    }
  }
}
