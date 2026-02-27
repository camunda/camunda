/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.9';
import {useElementInstancesStatistics} from './useElementInstancesStatistics';
import {getStatisticsByElement} from 'modules/utils/statistics/elementInstances';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';

const totalRunningInstancesForElementParser =
  (businessObjects?: BusinessObjects, flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) {
      return 0;
    }
    const statistics = getStatisticsByElement(response.items, businessObjects)[
      flowNodeId
    ];
    return (statistics?.active ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesForElementsParser =
  (flowNodeIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.active ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const totalRunningInstancesByElementParser =
  (businessObjects?: BusinessObjects) =>
  (
    response: GetProcessInstanceStatisticsResponseBody,
  ): Record<string, number> => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

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

const totalRunningInstancesVisibleForElementParser =
  (businessObjects?: BusinessObjects, flowNodeId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!flowNodeId) {
      return 0;
    }
    const statistics = getStatisticsByElement(response.items, businessObjects)[
      flowNodeId
    ];
    return (statistics?.active ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesVisibleForElementsParser =
  (flowNodeIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

    return flowNodeIds.reduce<Record<string, number>>((acc, flowNodeId) => {
      const active = statistics[flowNodeId]?.active ?? 0;
      const incidents = statistics[flowNodeId]?.incidents ?? 0;
      acc[flowNodeId] = active + incidents;
      return acc;
    }, {});
  };

const useTotalRunningInstancesForElement = (flowNodeId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesForElementParser(businessObjects, flowNodeId),
  );
};

const useTotalRunningInstancesForElements = (flowNodeIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesForElementsParser(flowNodeIds, businessObjects),
  );
};

const useTotalRunningInstancesByElement = () => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesByElementParser(businessObjects),
  );
};

const useTotalRunningInstancesVisibleForElement = (flowNodeId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesVisibleForElementParser(businessObjects, flowNodeId),
  );
};

const useTotalRunningInstancesVisibleForElements = (flowNodeIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesVisibleForElementsParser(flowNodeIds, businessObjects),
  );
};

export {
  useTotalRunningInstancesForElement,
  useTotalRunningInstancesForElements,
  useTotalRunningInstancesByElement,
  useTotalRunningInstancesVisibleForElement,
  useTotalRunningInstancesVisibleForElements,
};
