/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {BatchOperationDto} from 'modules/api/sharedTypes';
import {mockPostRequest} from '../mockRequest';

const mockApplyBatchOperation = () =>
  mockPostRequest<BatchOperationDto>('/api/process-instances/batch-operation');

const mockApplyOperation = () =>
  mockPostRequest<BatchOperationDto>(
    '/api/process-instances/:instanceId/operation'
  );

export {mockApplyBatchOperation, mockApplyOperation};
