/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */

import {CoreStatisticsDto} from 'modules/api/processInstances/fetchProcessCoreStatistics';
import {mockGetRequest} from '../mockRequest';

const mockFetchProcessCoreStatistics = () =>
  mockGetRequest<CoreStatisticsDto>('/api/process-instances/core-statistics');

export {mockFetchProcessCoreStatistics};
