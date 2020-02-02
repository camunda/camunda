/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate;

import static org.assertj.core.api.Assertions.assertThat;

import org.camunda.operate.property.OperateProperties;
import org.camunda.operate.qa.util.TestElasticsearchSchemaManager;
import org.camunda.operate.util.TestUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(
    classes = { TestApplication.class, OperateProperties.class, TestElasticsearchSchemaManager.class, Probes.class },    properties = {
      OperateProperties.PREFIX + ".elasticsearch.createSchema = false"
    }
)
public class ProbesTestIT{

  @Autowired
  private OperateProperties operateProperties;
  
  @Autowired
  private TestElasticsearchSchemaManager elasticsearchSchemaManager;
  
  @Autowired
  private Probes probes;
  
  @Before
  public void before() {
     operateProperties.getElasticsearch().setIndexPrefix("test-probes-"+TestUtil.createRandomString(5));
  }
  
  @After
  public void after() {
    elasticsearchSchemaManager.deleteSchemaQuietly();
    operateProperties.getElasticsearch().setDefaultIndexPrefix();
  }
  
  @Test
  public void testIsReady() {
    elasticsearchSchemaManager.createSchema();
    assertThat(probes.isReady()).isTrue();
  }
  
  @Test
  public void testIsNotReady() {
    assertThat(probes.isReady()).isFalse();
  }

  @Test
  public void testIsLive() {
    elasticsearchSchemaManager.createSchema();
    assertThat(probes.isLive()).isTrue();
  }
  
  @Test
  public void testIsNotLive() {
    assertThat(probes.isLive()).isFalse();
  }
  
}
