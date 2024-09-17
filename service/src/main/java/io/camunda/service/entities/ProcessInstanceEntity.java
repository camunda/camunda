/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProcessInstanceEntity(
    Long key,
    String processName,
    Integer processVersion,
    String bpmnProcessId,
    Long parentProcessInstanceKey,
    Long parentFlowNodeInstanceKey,
    String startDate,
    String endDate,
    String state,
    Boolean incident,
    Boolean hasActiveOperation,
    Long processDefinitionKey,
    String tenantId,
    String rootInstanceId,
    List<OperationEntity> operations,
    List<ProcessInstanceReference> callHierarchy) {}
