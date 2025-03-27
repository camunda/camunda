/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.CREATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.ERROR_TYPE;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.JOB_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.IncidentTemplate.STATE;

public class IncidentFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "incidentKey" -> KEY;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "errorType" -> ERROR_TYPE;
      case "errorMessage" -> ERROR_MSG;
      case "flowNodeId" -> FLOW_NODE_ID;
      case "flowNodeInstanceKey" -> FLOW_NODE_INSTANCE_KEY;
      case "creationTime" -> CREATION_TIME;
      case "state" -> STATE;
      case "jobKey" -> JOB_KEY;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
