/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.es.RetryElasticsearchClient;
import io.camunda.tasklist.management.HealthCheckIT.AddManagementPropertiesInitializer;
import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.camunda.tasklist.qa.util.TestSchemaManager;
import io.camunda.tasklist.schema.IndexSchemaValidator;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.util.TestUtil;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import io.camunda.tasklist.zeebe.PartitionHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestElasticsearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryElasticsearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      "graphql.servlet.websocket.enabled=false"
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = AddManagementPropertiesInitializer.class)
public class ProbesTestElasticSearchIT extends TasklistIntegrationTest {

  @Autowired private TasklistProperties tasklistProperties;
  @Autowired private TestSchemaManager schemaManager;
  @Autowired private IndexSchemaValidator indexSchemaValidator;
  @MockBean private PartitionHolder partitionHolder;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isElasticSearch());
  }

  @BeforeEach
  public void before() {
    mockPartitionHolder(partitionHolder);
    tasklistProperties
        .getElasticsearch()
        .setIndexPrefix("test-probes-" + TestUtil.createRandomString(5));
  }

  @AfterEach
  public void after() {
    schemaManager.deleteSchemaQuietly();
    tasklistProperties.getElasticsearch().setDefaultIndexPrefix();
  }

  @Test
  public void testIsReady() {
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
    enableCreateSchema(true);
    schemaManager.createSchema();
    assertThat(indexSchemaValidator.schemaExists()).isTrue();
  }

  @Test
  public void testIsNotReady() {
    enableCreateSchema(false);
    assertThat(indexSchemaValidator.schemaExists()).isFalse();
  }

  protected void enableCreateSchema(boolean createSchema) {
    tasklistProperties.getElasticsearch().setCreateSchema(createSchema);
  }
}
