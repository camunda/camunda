/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {mockGetRequest, mockPostRequest} from '../mockRequest';
import {StatisticEntity} from 'modules/api/processInstances';

const mockFetchVariables = (contextPath = '') =>
  mockPostRequest(`${contextPath}/api/process-instances/:instanceId/variables`);

const mockFetchProcessInstanceIncidents = (contextPath = '') =>
  mockGetRequest(`${contextPath}/api/process-instances/:instanceId/incidents`);

const mockFetchProcessInstanceDetailStatistics = (contextPath = '') =>
  mockGetRequest<StatisticEntity[]>(
    `${contextPath}/api/process-instances/:processInstanceId/statistics`
  );

export {
  mockFetchVariables,
  mockFetchProcessInstanceIncidents,
  mockFetchProcessInstanceDetailStatistics,
};
