/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

/**
 * This file contains types which are shared between multiple API endpoints.
 */

type BatchOperationDto = {
  id: string;
  name: string | null;
  type: OperationEntityType;
  startDate: string;
  endDate: string | null;
  username: string;
  instancesCount: number;
  operationsTotalCount: number;
  operationsFinishedCount: number;
};

export type {BatchOperationDto};
