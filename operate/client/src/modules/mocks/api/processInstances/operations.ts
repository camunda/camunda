/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BatchOperationDto} from 'modules/api/sharedTypes';
import {mockPostRequest} from '../mockRequest';

const mockApplyBatchOperation = () =>
  mockPostRequest<BatchOperationDto>('/api/process-instances/batch-operation');

const mockApplyOperation = () =>
  mockPostRequest<BatchOperationDto>(
    '/api/process-instances/:instanceId/operation',
  );

export {mockApplyBatchOperation, mockApplyOperation};
