/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.se;

import io.camunda.db.search.engine.config.DatabaseConfig;
import org.springframework.boot.autoconfigure.condition.AnyNestedCondition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

public class SearchEngineEnabledCondition extends AnyNestedCondition {

  public SearchEngineEnabledCondition() {
    super(ConfigurationPhase.PARSE_CONFIGURATION);
  }

  @ConditionalOnProperty(
      name = "camunda.database",
      havingValue = DatabaseConfig.ELASTICSEARCH,
      matchIfMissing = true)
  static class ElasticSearchEnabled {

  }

  @ConditionalOnProperty(
      name = "camunda.database",
      havingValue = DatabaseConfig.OPENSEARCH,
      matchIfMissing = true)
  static class OpenSearchEnabled {

  }
}
