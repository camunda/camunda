/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.NAME;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.SCOPE_KEY;
import static io.camunda.webapps.schema.descriptors.template.VariableTemplate.VALUE;

public class VariableFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "variableKey" -> KEY;
      case "name" -> NAME;
      case "value" -> VALUE;
      case "scopeKey" -> SCOPE_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
