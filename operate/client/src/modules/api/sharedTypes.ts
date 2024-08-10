/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
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
