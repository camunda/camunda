/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.test.it.factory;

import org.elasticsearch.client.RestHighLevelClient;
import org.mockito.Mockito;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

public class MockElasticsearchRestClientFactory implements FactoryBean<RestHighLevelClient> {

  private RestHighLevelClient spyedInstance;

  @Autowired
  private ApplicationContext applicationContext;

  @Override
  public RestHighLevelClient getObject() {
    return Mockito.mock(RestHighLevelClient.class);
  }

  @Override
  public Class<?> getObjectType() {
    return RestHighLevelClient.class;
  }

  @Override
  public boolean isSingleton() {
    return true;
  }
}
