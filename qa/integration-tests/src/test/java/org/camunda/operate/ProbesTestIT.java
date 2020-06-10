/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import org.camunda.operate.util.TestApplication;
import org.camunda.operate.util.TestUtil;
import org.camunda.operate.webapp.es.reader.Probes;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplication.class, OperateProperties.class, TestElasticsearchSchemaManager.class, Probes.class }
)
public class ProbesTestIT{

  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  private TestElasticsearchSchemaManager schemaManager;
  
  @Autowired
  private Probes probes;
  
  @Before
  public void before() {
     operateProperties.getElasticsearch().setIndexPrefix("test-probes-"+TestUtil.createRandomString(5));
  }
  
  @After
  public void after() {
    schemaManager.deleteSchemaQuietly();
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }
  
  @Test
  public void testIsReady() {
    enableCreateSchema(true);
    schemaManager.createSchema();
    assertThat(probes.isReady()).isTrue();
  }
  
  @Test
  public void testIsNotReady() {
    enableCreateSchema(false);
    assertThat(probes.isReady()).isFalse();
  }

  @Test
  public void testIsLive() {
    enableCreateSchema(true);
    schemaManager.createSchema();
    assertThat(probes.isLive()).isTrue();
  }
  
  @Test
  public void testIsNotLive() {
    enableCreateSchema(false);
    assertThat(probes.isLive()).isFalse();
  }
  
  protected void enableCreateSchema(boolean createSchema) {
    operateProperties.getElasticsearch().setCreateSchema(createSchema);
  }
}
