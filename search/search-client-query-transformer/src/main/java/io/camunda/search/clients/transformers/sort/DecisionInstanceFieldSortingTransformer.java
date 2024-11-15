/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

public class DecisionInstanceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "decisionInstanceKey" -> "key";
      case "decisionInstanceId" -> "id";
      case "decisionDefinitionId" -> "decisionId";
      case "decisionDefinitionKey" -> "decisionDefinitionId"; // yes, this is correct
      case "decisionDefinitionName" -> "decisionName";
      case "decisionDefinitionVersion" -> "decisionVersion";
      case "decisionDefinitionType" -> "decisionType";
      default -> domainField;
    };
  }

  @Override
  public String defaultSortField() {
    return "id"; // PK of DecisionInstanceEntity
  }
}
