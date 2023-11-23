/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.operate.webapp.api.v1.entities.opensearch;

public record OpensearchIncident(
  String creationTime,
  String errorType,
  String errorMessage,
  Long key,
  Long processDefinitionKey,
  Long processInstanceKey,
  String state,
  Long jobKey,
  String tenantId
){}
