/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.DECISION_REQUIREMENTS_ID;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.DecisionRequirementsIndex.VERSION;

public class DecisionRequirementsFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "decisionRequirementsKey" -> KEY;
      case "name" -> NAME;
      case "version" -> VERSION;
      case "decisionRequirementsId" -> DECISION_REQUIREMENTS_ID;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
