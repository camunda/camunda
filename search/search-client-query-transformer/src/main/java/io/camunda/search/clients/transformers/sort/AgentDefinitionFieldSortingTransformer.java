/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.sort;

import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.AGENT_TYPE;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.ELEMENT_ID;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.KEY;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.NAME;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_VERSION;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.PROCESS_DEFINITION_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.index.AgentDefinitionIndex.TENANT_ID;

public class AgentDefinitionFieldSortingTransformer implements FieldSortingTransformer {

  @Override
  public String apply(final String domainField) {
    return switch (domainField) {
      case "agentDefinitionKey" -> KEY; // ES/OS stores the key as "key", not "agentDefinitionKey"
      case "agentType" -> AGENT_TYPE;
      case "name" -> NAME;
      case "elementId" -> ELEMENT_ID;
      case "processDefinitionId" -> BPMN_PROCESS_ID;
      case "processDefinitionKey" -> PROCESS_DEFINITION_KEY;
      case "processDefinitionVersion" -> PROCESS_DEFINITION_VERSION;
      case "processDefinitionVersionTag" -> PROCESS_DEFINITION_VERSION_TAG;
      case "tenantId" -> TENANT_ID;
      default -> throw new IllegalArgumentException("Unknown sortField: " + domainField);
    };
  }
}
