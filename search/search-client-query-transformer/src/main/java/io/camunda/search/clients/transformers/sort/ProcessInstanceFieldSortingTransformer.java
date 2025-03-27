/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;

public class ProcessInstanceFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "processInstanceKey" -> KEY;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "processDefinitionName" -> PROCESS_NAME;
      case "processDefinitionVersion" -> PROCESS_VERSION;
      case "processDefinitionVersionTag" -> PROCESS_VERSION_TAG;
      case "processDefinitionKey" -> PROCESS_KEY;
      case "parentProcessInstanceKey" -> PARENT_PROCESS_INSTANCE_KEY;
      case "parentFlowNodeInstanceKey" -> PARENT_FLOW_NODE_INSTANCE_KEY;
      case "startDate" -> START_DATE;
      case "endDate" -> END_DATE;
      case "state" -> STATE;
      case "hasIncident" -> INCIDENT;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
