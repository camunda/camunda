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
import {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';

const totalRunningInstancesForFlowNodeParser =
  (businessObjects?: BusinessObjects, flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) return 0;
    const statistics = getStatisticsByFlowNode(response.items, businessObjects)[
      flowNodeId
    ];
    return (statistics?.active ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesForFlowNodesParser =
  (flowNodeIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByFlowNode(response.items, businessObjects);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.active ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const totalRunningInstancesByFlowNodeParser =
  (businessObjects?: BusinessObjects) =>
  (
    response: GetProcessInstanceStatisticsResponseBody,
  ): Record<string, number> => {
    const statistics = getStatisticsByFlowNode(response.items, businessObjects);

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
  (businessObjects?: BusinessObjects, flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) return 0;
    const statistics = getStatisticsByFlowNode(response.items, businessObjects)[
      flowNodeId
    ];
    return (statistics?.filteredActive ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesVisibleForFlowNodesParser =
  (flowNodeIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByFlowNode(response.items, businessObjects);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.filteredActive ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const useTotalRunningInstancesForFlowNode = (flowNodeId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    totalRunningInstancesForFlowNodeParser(businessObjects, flowNodeId),
  );
};

const useTotalRunningInstancesForFlowNodes = (flowNodeIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    totalRunningInstancesForFlowNodesParser(flowNodeIds, businessObjects),
  );
};

const useTotalRunningInstancesByFlowNode = () => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    totalRunningInstancesByFlowNodeParser(businessObjects),
  );
};

const useTotalRunningInstancesVisibleForFlowNode = (flowNodeId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    totalRunningInstancesVisibleForFlowNodeParser(businessObjects, flowNodeId),
  );
};

const useTotalRunningInstancesVisibleForFlowNodes = (flowNodeIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useFlownodeInstancesStatistics(
    totalRunningInstancesVisibleForFlowNodesParser(
      flowNodeIds,
      businessObjects,
    ),
  );
};

export {
  useTotalRunningInstancesForFlowNode,
  useTotalRunningInstancesForFlowNodes,
  useTotalRunningInstancesByFlowNode,
  useTotalRunningInstancesVisibleForFlowNode,
  useTotalRunningInstancesVisibleForFlowNodes,
};
