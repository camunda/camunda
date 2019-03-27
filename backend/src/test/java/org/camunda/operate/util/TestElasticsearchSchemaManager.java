/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.operate.util;

import org.camunda.operate.es.ElasticsearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class TestElasticsearchSchemaManager extends ElasticsearchSchemaManager {

  private static final Logger logger = LoggerFactory.getLogger(TestElasticsearchSchemaManager.class);

  @Override
  public boolean initializeSchema() {
    //do nothing
    logger.info("INIT: no schema will be created");
    return true;
  }
}
