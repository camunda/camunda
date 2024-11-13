/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.camunda.tasklist.management.SearchEngineHealthIndicator;
import io.camunda.tasklist.os.RetryOpenSearchClient;
import io.camunda.tasklist.property.TasklistProperties;
import io.camunda.tasklist.qa.util.TestOpenSearchSchemaManager;
import io.camunda.tasklist.qa.util.TestSchemaManager;
import io.camunda.tasklist.qa.util.TestUtil;
import io.camunda.tasklist.schema.v86.IndexSchemaValidator;
import io.camunda.tasklist.util.TasklistIntegrationTest;
import io.camunda.tasklist.util.TestApplication;
import io.camunda.tasklist.webapp.security.WebSecurityConfig;
import io.camunda.tasklist.webapp.security.oauth.OAuth2WebConfigurer;
import io.camunda.tasklist.zeebe.PartitionHolder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = {
      TestOpenSearchSchemaManager.class,
      TestApplication.class,
      SearchEngineHealthIndicator.class,
      WebSecurityConfig.class,
      OAuth2WebConfigurer.class,
      RetryOpenSearchClient.class,
    },
    properties = {
      TasklistProperties.PREFIX + ".opensearch.createSchema = false",
    },
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles({"tasklist", "test"})
public class ProbesTestOpenSearchIT extends TasklistIntegrationTest {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TestSchemaManager schemaManager;

  @Autowired private IndexSchemaValidator indexSchemaValidator;

  @MockBean private PartitionHolder partitionHolder;

  @BeforeAll
  public static void beforeClass() {
    assumeTrue(TestUtil.isOpenSearch());
  }

  @Override
  @BeforeEach
  public void before() {
    mockPartitionHolder(partitionHolder);
    tasklistProperties
        .getOpenSearch()
        .setIndexPrefix("test-probes-" + TestUtil.createRandomString(5));
  }

  @AfterEach
  public void after() {
    schemaManager.deleteSchemaQuietly();
    tasklistProperties.getOpenSearch().setDefaultIndexPrefix();
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

  protected void enableCreateSchema(final boolean createSchema) {
    tasklistProperties.getOpenSearch().setCreateSchema(createSchema);
  }
}
