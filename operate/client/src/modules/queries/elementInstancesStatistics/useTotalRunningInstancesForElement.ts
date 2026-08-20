/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {type GetProcessInstanceStatisticsResponseBody} from '@camunda/camunda-api-zod-schemas/8.10';
import {useElementInstancesStatistics} from './useElementInstancesStatistics';
import {getStatisticsByElement} from 'modules/utils/statistics/elementInstances';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {useBusinessObjects} from '../processDefinitions/useBusinessObjects';

const totalRunningInstancesForElementParser =
  (businessObjects?: BusinessObjects, elementId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!elementId) {
      return 0;
    }
    const statistics = getStatisticsByElement(response.items, businessObjects)[
      elementId
    ];
    return (statistics?.active ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesForElementsParser =
  (elementIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

    return elementIds.reduce<Record<string, number>>((acc, elementId) => {
      const active = statistics[elementId]?.active ?? 0;
      const incidents = statistics[elementId]?.incidents ?? 0;
      acc[elementId] = active + incidents;
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
      (acc, elementId) => {
        const active = statistics[elementId]?.active ?? 0;
        const incidents = statistics[elementId]?.incidents ?? 0;
        acc[elementId] = active + incidents;
        return acc;
      },
      {},
    );
  };

const totalRunningInstancesVisibleForElementParser =
  (businessObjects?: BusinessObjects, elementId?: string) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    if (!elementId) {
      return 0;
    }
    const statistics = getStatisticsByElement(response.items, businessObjects)[
      elementId
    ];
    return (statistics?.active ?? 0) + (statistics?.incidents ?? 0);
  };

const totalRunningInstancesVisibleForElementsParser =
  (elementIds: string[], businessObjects?: BusinessObjects) =>
  (response: GetProcessInstanceStatisticsResponseBody) => {
    const statistics = getStatisticsByElement(response.items, businessObjects);

    return elementIds.reduce<Record<string, number>>((acc, elementId) => {
      const active = statistics[elementId]?.active ?? 0;
      const incidents = statistics[elementId]?.incidents ?? 0;
      acc[elementId] = active + incidents;
      return acc;
    }, {});
  };

const useTotalRunningInstancesForElement = (elementId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesForElementParser(businessObjects, elementId),
  );
};

const useTotalRunningInstancesForElements = (elementIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesForElementsParser(elementIds, businessObjects),
  );
};

const useTotalRunningInstancesByElement = () => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesByElementParser(businessObjects),
  );
};

const useTotalRunningInstancesVisibleForElement = (elementId?: string) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesVisibleForElementParser(businessObjects, elementId),
  );
};

const useTotalRunningInstancesVisibleForElements = (elementIds: string[]) => {
  const {data: businessObjects} = useBusinessObjects();

  return useElementInstancesStatistics(
    totalRunningInstancesVisibleForElementsParser(elementIds, businessObjects),
  );
};

export {
  useTotalRunningInstancesForElement,
  useTotalRunningInstancesForElements,
  useTotalRunningInstancesByElement,
  useTotalRunningInstancesVisibleForElement,
  useTotalRunningInstancesVisibleForElements,
};
