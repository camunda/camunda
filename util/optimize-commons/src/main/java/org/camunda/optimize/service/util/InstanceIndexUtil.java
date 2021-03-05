/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.camunda.optimize.service.es.schema.index.DecisionInstanceIndex;
import org.elasticsearch.ElasticsearchStatusException;

import java.util.Arrays;

import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_INDEX_PREFIX;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.DECISION_INSTANCE_MULTI_ALIAS;
import static org.camunda.optimize.upgrade.es.ElasticsearchConstants.INDEX_NOT_FOUND_EXCEPTION_TYPE;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InstanceIndexUtil {

  public static String getDecisionInstanceIndexAliasName(final String decisionDefinitionKey) {
    return new DecisionInstanceIndex(decisionDefinitionKey).getIndexName();
  }

  public static boolean isDecisionInstanceIndexNotFoundException(final ElasticsearchStatusException e) {
    return Arrays.stream(e.getSuppressed())
      .map(Throwable::getMessage)
      .anyMatch(msg -> msg.contains(INDEX_NOT_FOUND_EXCEPTION_TYPE)
        && (msg.contains(DECISION_INSTANCE_INDEX_PREFIX)) || msg.contains(DECISION_INSTANCE_MULTI_ALIAS));
  }
}
