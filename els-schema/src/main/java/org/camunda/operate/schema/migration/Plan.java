/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.schema.migration;

import java.io.IOException;
import java.util.List;

import org.camunda.operate.exceptions.MigrationException;
import org.elasticsearch.client.RestHighLevelClient;
/**
 * A plan consists of executable Steps.
 * The plan can be execute on an elasticsearch client.
 */
public interface Plan {

  List<Step> getSteps();

  void executeOn(final RestHighLevelClient esClient) throws IOException, MigrationException;

}
