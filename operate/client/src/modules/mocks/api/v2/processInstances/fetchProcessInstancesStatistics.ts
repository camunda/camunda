/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {ProcessInstancesStatisticsDto} from 'modules/api/v2/processInstances/fetchProcessInstancesStatistics';
import {mockPostRequest} from '../../mockRequest';

const mockFetchProcessInstancesStatistics = () =>
  mockPostRequest<ProcessInstancesStatisticsDto[]>(
    '/v2/process-instances/statistics',
  );

export {mockFetchProcessInstancesStatistics};
