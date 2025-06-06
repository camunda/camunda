/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.FlowNodeInstanceTemplate.*;

public class FlowNodeInstanceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "flowNodeInstanceKey" -> KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "startDate" -> START_DATE;
      case "endDate" -> END_DATE;
      case "flowNodeId" -> FLOW_NODE_ID;
      case "flowNodeName" -> FLOW_NODE_NAME;
      case "type" -> TYPE;
      case "state" -> STATE;
      case "incidentKey" -> INCIDENT_KEY;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
