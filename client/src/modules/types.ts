/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */

type OperationType =
  | 'RESOLVE_INCIDENT'
  | 'CANCEL_WORKFLOW_INSTANCE'
  | 'UPDATE_VARIABLE';

export type {OperationType};
