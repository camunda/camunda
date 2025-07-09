/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  FlowNodeInstanceDto,
  FlowNodeInstancesDto,
} from 'modules/api/fetchFlowNodeInstances';
import {mockPostRequest} from './mockRequest';

const mockFetchFlowNodeInstances = (contextPath = '') =>
  mockPostRequest<FlowNodeInstancesDto<FlowNodeInstanceDto>>(
    `${contextPath}/api/flow-node-instances`,
  );

export {mockFetchFlowNodeInstances};
