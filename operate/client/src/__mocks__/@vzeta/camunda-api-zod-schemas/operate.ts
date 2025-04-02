/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

export const GetProcessDefinitionStatisticsRequestBody = {};
export const GetProcessDefinitionStatisticsResponseBody = {};
export const ProcessDefinitionStatistic = {};
export const endpoints = {
  getProcessDefinitionStatistics: {
    method: 'POST',
    getUrl: ({
      processDefinitionKey,
      statisticName,
    }: {
      processDefinitionKey: string;
      statisticName: string;
    }) =>
      `/v2/process-definitions/${processDefinitionKey}/statistics/${statisticName}`,
  },
};
