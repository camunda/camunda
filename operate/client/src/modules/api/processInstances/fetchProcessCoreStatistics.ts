/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {requestAndParse} from 'modules/request';

type CoreStatisticsDto = {
  running: number;
  active: number;
  withIncidents: number;
};

const fetchProcessCoreStatistics = async (
  options?: Parameters<typeof requestAndParse>[1],
) => {
  return requestAndParse<CoreStatisticsDto>(
    {
      url: '/api/process-instances/core-statistics',
    },
    options,
  );
};

export {fetchProcessCoreStatistics};
export type {CoreStatisticsDto};
