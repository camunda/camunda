/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.util.List;

import org.elasticsearch.client.RestHighLevelClient;
/**
 * A plan consists of executable Steps.
 * The plan can be execute on an elasticsearch client.
 */
public interface Plan {

  public List<Step> getSteps();

  public void executeOn(final RestHighLevelClient esClient);

}
