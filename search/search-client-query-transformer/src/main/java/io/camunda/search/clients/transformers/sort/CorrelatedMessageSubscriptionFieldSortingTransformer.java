/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageSubscriptionTemplate.*;

public class CorrelatedMessageSubscriptionFieldSortingTransformer
    implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "correlationKey" -> CORRELATION_KEY;
      case "correlationTime" -> CORRELATION_TIME;
      case "flowNodeId" -> FLOW_NODE_ID;
      case "flowNodeInstanceKey" -> FLOW_NODE_INSTANCE_KEY;
      case "messageKey" -> MESSAGE_KEY;
      case "messageName" -> MESSAGE_NAME;
      case "partitionId" -> PARTITION_ID;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processInstanceKey" -> PROCESS_INSTANCE_KEY;
      case "subscriptionKey" -> SUBSCRIPTION_KEY;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown field: " + domainField);
    };
  }

  @Override
  public String defaultSortField() {
    return "messageKey";
  }
}
