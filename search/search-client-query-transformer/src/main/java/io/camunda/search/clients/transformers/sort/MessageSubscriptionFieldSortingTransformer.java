/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.DATE_TIME;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.EVENT_TYPE;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.EventTemplate.KEY;

public class MessageSubscriptionFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "messageSubscriptionKey" -> KEY;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "flowNodeId" -> FLOW_NODE_ID;
      case "flowNodeInstanceKey" -> FLOW_NODE_INSTANCE_KEY;
      case "messageSubscriptionType" -> EVENT_TYPE;
      case "dateTime" -> DATE_TIME;
      case "messageName" -> "metadata.messageName";
      case "correlationKey" -> "metadata.correlationKey";
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }
}
