/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {mockDeleteRequest} from '../mockRequest';
import type {OperationEntity} from 'modules/types/operate';

const mockApplyDeleteDefinitionOperation = () =>
  mockDeleteRequest<OperationEntity>('/api/decisions/:decisionDefinitionId');

export {mockApplyDeleteDefinitionOperation};
