/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.tasklist.qa.util;

import io.camunda.tasklist.data.conditionals.OpenSearchCondition;
import io.camunda.tasklist.schema.v86.manager.OpenSearchSchemaManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component("tasklistSchemaManager")
@Profile("test")
@Conditional(OpenSearchCondition.class)
public class TestOpenSearchSchemaManager extends OpenSearchSchemaManager
    implements TestSchemaManager {

  private static final Logger LOGGER = LoggerFactory.getLogger(TestOpenSearchSchemaManager.class);

  public void deleteSchema() {
    final String prefix = tasklistProperties.getOpenSearch().getIndexPrefix();
    LOGGER.info("Removing indices " + prefix + "*");
    retryOpenSearchClient.deleteIndicesFor(prefix + "*");
    retryOpenSearchClient.deleteTemplatesFor(prefix + "*");
  }

  public void deleteSchemaQuietly() {
    try {
      deleteSchema();
    } catch (Exception t) {
      LOGGER.debug(t.getMessage());
    }
  }
}
