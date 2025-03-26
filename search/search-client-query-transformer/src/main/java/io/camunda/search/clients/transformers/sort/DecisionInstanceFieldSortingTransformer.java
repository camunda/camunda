/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_FAILURE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.TENANT_ID;

public class DecisionInstanceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "decisionInstanceKey" -> KEY;
      case "decisionInstanceId" -> ID;
      case "state" -> STATE;
      case "evaluationDate" -> EVALUATION_DATE;
      case "evaluationFailure" -> EVALUATION_FAILURE;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "decisionDefinitionKey" -> DECISION_DEFINITION_ID; // yes, this is correct
      case "decisionDefinitionId" -> DECISION_ID;
      case "decisionDefinitionName" -> DECISION_NAME;
      case "decisionDefinitionVersion" -> DECISION_VERSION;
      case "decisionDefinitionType" -> DECISION_TYPE;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return ID; // PK of DecisionInstanceEntity
  }
}
