/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {
  endpoints,
  GetProcessInstanceStatisticsResponseBody,
} from '@vzeta/camunda-api-zod-schemas/operate';
import {RequestResult, requestWithThrow} from 'modules/request';

const fetchFlownodeInstancesStatistics = async (
  processInstanceKey: string,
): RequestResult<GetProcessInstanceStatisticsResponseBody> => {
  return requestWithThrow<GetProcessInstanceStatisticsResponseBody>({
    url: endpoints.getProcessInstanceStatistics.getUrl({
      processInstanceKey,
      statisticName: 'flownode-instances',
    }),
    method: endpoints.getProcessInstanceStatistics.method,
  });
};

export {fetchFlownodeInstancesStatistics};
