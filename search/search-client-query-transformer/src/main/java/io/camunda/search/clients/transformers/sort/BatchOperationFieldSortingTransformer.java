/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.BatchOperationTemplate.TYPE;

public class BatchOperationFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "batchOperationKey" -> ID;
      case "state" -> STATE;
      case "operationType" -> TYPE;
      case "startDate" -> START_DATE;
      case "endDate" -> END_DATE;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return ID;
  }
}
