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
import io.camunda.tasklist.v86.schema.indices.TasklistImportPositionIndex;
import io.camunda.tasklist.v86.schema.indices.TasklistUserIndex;
import io.camunda.tasklist.v86.schema.indices.TasklistVariableIndex;
import io.camunda.tasklist.v86.schema.manager.ElasticsearchSchemaManager;
import io.camunda.tasklist.v86.schema.templates.TasklistTaskTemplate;
import io.camunda.webapps.schema.descriptors.operate.index.ProcessIndex;
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

  @Autowired protected TasklistTaskTemplate taskTemplate;

  @Autowired protected TasklistVariableIndex variableIndex;

  @Autowired protected ProcessIndex processIndex;

  @Autowired protected TestContext testContext;

  @Autowired protected RestHighLevelClient esClient;

  @Autowired protected RetryElasticsearchClient retryElasticsearchClient;

  @Autowired protected TasklistImportPositionIndex importPositionIndex;

  @Autowired protected TasklistUserIndex userIndex;

  protected void assumeThatProcessIsUnderTest(final String bpmnProcessId) {
    assumeTrue(testContext.getProcessesToAssert().contains(bpmnProcessId));
  }
}
