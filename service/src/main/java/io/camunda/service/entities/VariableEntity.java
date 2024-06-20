/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.entities;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public final record VariableEntity(
    Long key,
    String name,
    String value,
    String fullValue,
    boolean isPreview,
    Long scopeKey,
    Long processInstanceKey,
    Long processDefinitionKey,
    String bpmnProcessId,
    String tenantId) {}
