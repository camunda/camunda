/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package io.zeebe.tasklist;

import static org.assertj.core.api.Assertions.assertThat;

import io.zeebe.tasklist.management.ElsIndicesCheck;
import io.zeebe.tasklist.property.TasklistProperties;
import io.zeebe.tasklist.qa.util.TestElasticsearchSchemaManager;
import io.zeebe.tasklist.util.TestApplication;
import io.zeebe.tasklist.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = {TestApplication.class, TestElasticsearchSchemaManager.class},
    properties = {
      TasklistProperties.PREFIX + ".elasticsearch.createSchema = false",
      "graphql.servlet.websocket.enabled=false"
    })
public class ProbesTestIT {

  @Autowired private TasklistProperties tasklistProperties;

  @Autowired private TestElasticsearchSchemaManager schemaManager;

  @Autowired private ElsIndicesCheck probes;

  @Before
  public void before() {
    tasklistProperties
        .getElasticsearch()
        .setIndexPrefix("test-probes-" + TestUtil.createRandomString(5));
  }

  @After
  public void after() {
    schemaManager.deleteSchemaQuietly();
    tasklistProperties.getElasticsearch().setDefaultIndexPrefix();
  }

  @Test
  public void testIsReady() {
    assertThat(probes.indicesArePresent()).isFalse();
    enableCreateSchema(true);
    schemaManager.createSchema();
    assertThat(probes.indicesArePresent()).isTrue();
  }

  @Test
  public void testIsNotReady() {
    enableCreateSchema(false);
    assertThat(probes.indicesArePresent()).isFalse();
  }

  protected void enableCreateSchema(boolean createSchema) {
    tasklistProperties.getElasticsearch().setCreateSchema(createSchema);
  }
}
