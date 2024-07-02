/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.migration.util;

import static org.junit.Assume.assumeTrue;

import io.camunda.tasklist.JacksonConfig;
import io.camunda.tasklist.es.ElasticsearchInternalTask;
import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.qa.util.TestContext;
import io.camunda.tasklist.schema.indices.ImportPositionIndex;
import io.camunda.tasklist.schema.indices.ProcessIndex;
import io.camunda.tasklist.schema.indices.UserIndex;
import io.camunda.tasklist.schema.indices.VariableIndex;
import io.camunda.tasklist.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.schema.templates.TaskTemplate;
import org.elasticsearch.client.RestHighLevelClient;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.AnnotationConfigContextLoader;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(
    classes = {
      TestConfig.class,
      ElasticsearchSchemaManager.class,
      RetryElasticsearchClient.class,
      ElasticsearchInternalTask.class,
      JacksonConfig.class,
      TestContext.class,
    },
    loader = AnnotationConfigContextLoader.class)
@TestPropertySource(locations = "/test.properties")
public abstract class AbstractMigrationTest {

  @Autowired protected EntityReader entityReader;

  @Autowired protected TaskTemplate taskTemplate;

  @Autowired protected VariableIndex variableIndex;

  @Autowired protected ProcessIndex processIndex;

  @Autowired protected TestContext testContext;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired protected ImportPositionIndex importPositionIndex;

  @Autowired protected UserIndex userIndex;

  protected void assumeThatProcessIsUnderTest(final String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }
}
