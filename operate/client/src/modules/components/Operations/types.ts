/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

type OperationType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_PROCESS_INSTANCE'
  | 'DELETE_PROCESS_INSTANCE'
  | 'ENTER_MODIFICATION_MODE';

type OperationConfig = {
  type: OperationType;
  onExecute: () => void;
  disabled?: boolean;
  label?: string;
};

export type {OperationConfig};
