/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.client.api.search.response;

public interface Incident {

  Long getKey();

  Long getProcessDefinitionKey();

  Long getProcessInstanceKey();

  String getType();

  String getFlowNodeId();

  String getFlowNodeInstanceId();

  String getCreationTime();

  String getState();

  Long getJobKey();

  Boolean getHasActiveOperation();

  Operation getOperation();

  ProcessInstanceReference getProcessInstanceReference();

  DecisionInstanceReference getDecisionInstanceReference();
}
