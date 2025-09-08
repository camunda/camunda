/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.ProcessInstanceDependant.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.CORRELATION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.CORRELATION_TIME;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.FLOW_NODE_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.MESSAGE_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.MESSAGE_NAME;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.SUBSCRIPTION_KEY;
import static io.camunda.webapps.schema.descriptors.template.CorrelatedMessageTemplate.TENANT_ID;

public class CorrelatedMessagesFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "correlationKey" -> CORRELATION_KEY;
      case "correlationTime" -> CORRELATION_TIME;
      case "elementId" -> FLOW_NODE_ID;
      case "elementInstanceKey" -> FLOW_NODE_INSTANCE_KEY;
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
}