/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {GetProcessInstanceStatisticsResponseBody} from '@vzeta/camunda-api-zod-schemas/operate';
import {useFlownodeInstancesStatistics} from './useFlownodeInstancesStatistics';
import {getStatisticsByFlowNode} from 'modules/utils/statistics/flownodeInstances';

const totalRunningInstancesForFlowNodeParser =
  (flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) return 0;
    return (
      (getStatisticsByFlowNode(response.items)[flowNodeId]?.active ?? 0) +
      (getStatisticsByFlowNode(response.items)[flowNodeId]?.incidents ?? 0)
    );
  };

const totalRunningInstancesForFlowNodesParser =
  (flowNodeIds: string[]) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByFlowNode(response.items);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.active ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const totalRunningInstancesByFlowNodeParser = (
  response: GetProcessInstanceStatisticsResponseBody,
): Record<string, number> => {
  const statistics = getStatisticsByFlowNode(response.items);

  return Object.keys(statistics).reduce<Record<string, number>>(
    (acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.active ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    },
    {},
  );
};

const totalRunningInstancesVisibleForFlowNodeParser =
  (flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) return 0;
    return (
      (getStatisticsByFlowNode(response.items)[flowNodeId]?.filteredActive ??
        0) +
      (getStatisticsByFlowNode(response.items)[flowNodeId]?.incidents ?? 0)
    );
  };

const totalRunningInstancesVisibleForFlowNodesParser =
  (flowNodeIds: string[]) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByFlowNode(response.items);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.filteredActive ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const useTotalRunningInstancesForFlowNode = (flowNodeId?: string) => {
  return useFlownodeInstancesStatistics(
    totalRunningInstancesForFlowNodeParser(flowNodeId),
  );
};

const useTotalRunningInstancesForFlowNodes = (flowNodeIds: string[]) => {
  return useFlownodeInstancesStatistics(
    totalRunningInstancesForFlowNodesParser(flowNodeIds),
  );
};

const useTotalRunningInstancesByFlowNode = () => {
  return useFlownodeInstancesStatistics(totalRunningInstancesByFlowNodeParser);
};

const useTotalRunningInstancesVisibleForFlowNode = (flowNodeId?: string) => {
  return useFlownodeInstancesStatistics(
    totalRunningInstancesVisibleForFlowNodeParser(flowNodeId),
  );
};

const useTotalRunningInstancesVisibleForFlowNodes = (flowNodeIds: string[]) => {
  return useFlownodeInstancesStatistics(
    totalRunningInstancesVisibleForFlowNodesParser(flowNodeIds),
  );
};

export {
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesForFlowNodes,
  useTotalRunningInstancesByFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
  useTotalRunningInstancesVisibleForFlowNodes,
};
