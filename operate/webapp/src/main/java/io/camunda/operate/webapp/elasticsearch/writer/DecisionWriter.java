/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.operate.webapp.elasticsearch.writer;

import io.camunda.operate.schema.indices.DecisionIndex;
import io.camunda.operate.schema.indices.DecisionRequirementsIndex;
import io.camunda.operate.schema.templates.DecisionInstanceTemplate;
import io.camunda.operate.store.DecisionStore;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DecisionWriter implements io.camunda.operate.webapp.writer.DecisionWriter {

  private static final Logger LOGGER = LoggerFactory.getLogger(DecisionWriter.class);

  @Autowired private DecisionRequirementsIndex decisionRequirementsIndex;

  @Autowired private DecisionIndex decisionIndex;

  @Autowired private DecisionInstanceTemplate decisionInstanceTemplate;

  @Autowired private DecisionStore decisionStore;

  @Override
  public long deleteDecisionRequirements(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(
        decisionRequirementsIndex.getAlias(),
        DecisionRequirementsIndex.KEY,
        String.valueOf(decisionRequirementsKey));
  }

  @Override
  public long deleteDecisionDefinitionsFor(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(
        decisionIndex.getAlias(),
        DecisionIndex.DECISION_REQUIREMENTS_KEY,
        String.valueOf(decisionRequirementsKey));
  }

  @Override
  public long deleteDecisionInstancesFor(long decisionRequirementsKey) throws IOException {
    return decisionStore.deleteDocuments(
        decisionInstanceTemplate.getAlias(),
        DecisionInstanceTemplate.DECISION_REQUIREMENTS_KEY,
        String.valueOf(decisionRequirementsKey));
  }
}
