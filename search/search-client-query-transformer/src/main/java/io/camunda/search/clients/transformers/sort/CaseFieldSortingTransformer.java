/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.CaseIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.BUSINESS_ID;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.END_DATE;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.START_DATE;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.STATE;
import static io.camunda.webapps.schema.descriptors.index.CaseIndex.TITLE;

public class CaseFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "caseKey" -> KEY;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "businessId" -> BUSINESS_ID;
      case "title" -> TITLE;
      case "state" -> STATE;
      case "startDate" -> START_DATE;
      case "endDate" -> END_DATE;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return KEY;
  }
}
