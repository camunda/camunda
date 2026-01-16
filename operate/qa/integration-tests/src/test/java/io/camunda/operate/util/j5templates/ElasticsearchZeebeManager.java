/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.util.j5templates;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import io.camunda.operate.conditions.ElasticsearchCondition;
import io.camunda.operate.property.OperateProperties;
import io.camunda.operate.qa.util.TestContainerUtil;
import io.camunda.operate.util.IndexPrefixHolder;
import io.camunda.operate.util.TestUtil;
import io.camunda.security.configuration.SecurityConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Component;

@Conditional(ElasticsearchCondition.class)
@Component
public class ElasticsearchZeebeManager extends ZeebeContainerManager {
  private static final Logger LOGGER = LoggerFactory.getLogger(ElasticsearchZeebeManager.class);

  private final ElasticsearchClient esClient;

  public ElasticsearchZeebeManager(
      final OperateProperties operateProperties,
      final SecurityConfiguration securityConfiguration,
      final TestContainerUtil testContainerUtil,
      final ElasticsearchClient esClient,
      final IndexPrefixHolder indexPrefixHolder) {
    super(
        operateProperties,
        securityConfiguration,
        testContainerUtil,
        indexPrefixHolder.createNewIndexPrefix());
    this.esClient = esClient;
  }

  @Override
  protected void removeIndices() {
    TestUtil.removeAllIndices(esClient, prefix);
  }
}
