/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

// Conditional evaluation response field definitions
export const conditionalEvaluationResponseRequiredFields: string[] = [
  'processInstances',
];

export const conditionalProcessInstanceItemRequiredFields: string[] = [
  'processDefinitionKey',
  'processInstanceKey',
];

// Conditional evaluation request builders
export function EVALUATE_CONDITIONAL() {
  return {
    variables: {
      x: 15,
    },
  };
}

export function EVALUATE_CONDITIONAL_WITH_TENANT(tenantId: string) {
  return {
    tenantId,
    variables: {
      x: 15,
    },
  };
}

export function EVALUATE_CONDITIONAL_WITH_PROCESS_DEF_KEY(
  processDefinitionKey: string,
) {
  return {
    processDefinitionKey,
    variables: {
      x: 15,
    },
  };
}

export function EVALUATE_CONDITIONAL_MULTIPLE_CONDITIONS() {
  return {
    variables: {
      conditionA: true,
      conditionB: true,
    },
  };
}

export function EVALUATE_CONDITIONAL_NO_MATCH() {
  return {
    variables: {
      x: 5,
    },
  };
}

export function EVALUATE_CONDITIONAL_PARTIAL_MATCH() {
  return {
    variables: {
      conditionA: true,
      conditionB: false,
    },
  };
}
