/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.BATCH_OPERATION_ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.ITEM_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.OperationTemplate.STATE;

public class BatchOperationItemFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "batchOperationKey" -> BATCH_OPERATION_ID;
      case "state" -> STATE;
      case "itemKey" -> ITEM_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return ID;
  }
}
