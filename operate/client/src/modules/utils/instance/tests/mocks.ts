/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const mockIncidentInstance = {
  id: '8590375632-2',
  state: 'INCIDENT',
} as const;

const mockActiveInstance = {
  id: '8590375632-2',
  state: 'ACTIVE',
} as const;

const mockCompletedInstance = {
  id: '8590375632-2',
  state: 'COMPLETED',
} as const;
const mockCanceledInstance = {
  id: '8590375632-2',
  state: 'CANCELED',
} as const;

export {
  mockIncidentInstance,
  mockActiveInstance,
  mockCompletedInstance,
  mockCanceledInstance,
};
