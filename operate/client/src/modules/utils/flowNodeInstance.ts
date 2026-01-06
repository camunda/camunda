/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {ProcessInstance} from '@camunda/camunda-api-zod-schemas/8.8';
import {when} from 'mobx';
import {
  flowNodeInstanceStore,
  MAX_INSTANCES_PER_REQUEST,
  MAX_INSTANCES_STORED,
  type FlowNodeInstance,
  type FlowNodeInstances,
} from 'modules/stores/flowNodeInstance';
import {isInstanceRunning} from './instance';
import {fetchFlowNodeInstances} from 'modules/api/fetchFlowNodeInstances';
import {modificationsStore} from 'modules/stores/modifications';
import type {ProcessInstanceEntity} from 'modules/types/operate';

const init = (processInstance?: ProcessInstance) => {
  flowNodeInstanceStore.instanceExecutionHistoryDisposer = when(
    () => processInstance?.processInstanceKey !== undefined,
    () => {
      const instanceId = processInstance?.processInstanceKey;
      fetchInstanceExecutionHistory(processInstance)(instanceId!);
      startPolling(processInstance);
    },
  );

  flowNodeInstanceStore.instanceFinishedDisposer = when(
    () => !processInstance || !isInstanceRunning(processInstance),
    () => flowNodeInstanceStore.stopPolling(),
  );
};

const pollInstances = async (processInstance?: ProcessInstance) => {
  const processInstanceId = processInstance?.processInstanceKey;
  if (
    !processInstance ||
    !processInstanceId ||
    !isInstanceRunning(processInstance)
  ) {
    return;
  }

  const queries = Object.entries(flowNodeInstanceStore.state.flowNodeInstances)
    .filter(([treePath, flowNodeInstance]) => {
      return flowNodeInstance.running || treePath === processInstanceId;
    })
    .map(([treePath, flowNodeInstance]) => {
      return {
        treePath,
        processInstanceId,
        pageSize:
          // round up to a multiple of MAX_INSTANCES_PER_REQUEST
          Math.ceil(
            flowNodeInstance.children.length / MAX_INSTANCES_PER_REQUEST,
          ) * MAX_INSTANCES_PER_REQUEST,
        searchAfterOrEqual: flowNodeInstance.children[0]?.sortValues,
      };
    });

  if (queries.length === 0) {
    return;
  }

  flowNodeInstanceStore.isPollRequestRunning = true;
  const response = await fetchFlowNodeInstances(queries, {isPolling: true});

  if (response.isSuccess) {
    if (flowNodeInstanceStore.intervalId !== null) {
      flowNodeInstanceStore.handlePollSuccess(response.data ?? {});
    }
  } else {
    flowNodeInstanceStore.handleFetchFailure();
  }

  flowNodeInstanceStore.isPollRequestRunning = false;
};

const startPolling = (
  processInstance?: ProcessInstance,
  options: {runImmediately?: boolean} = {runImmediately: false},
) => {
  if (
    document.visibilityState === 'hidden' ||
    (processInstance && !isInstanceRunning(processInstance)) ||
    flowNodeInstanceStore.intervalId !== null
  ) {
    return;
  }

  if (options.runImmediately) {
    pollInstances(processInstance);
  }

  flowNodeInstanceStore.intervalId = setInterval(() => {
    if (!flowNodeInstanceStore.isPollRequestRunning) {
      pollInstances(processInstance);
    }
  }, 5000);
};

const fetchNext = async (
  treePath: string,
  processInstance?: ProcessInstance,
) => {
  if (
    ['fetching-next', 'fetching-prev', 'fetching'].includes(
      flowNodeInstanceStore.state.status,
    )
  ) {
    return;
  }

  const children =
    flowNodeInstanceStore.state.flowNodeInstances[treePath]?.children;
  if (children === undefined) {
    return;
  }

  flowNodeInstanceStore.startFetchNext();

  const sortValues = children && children[children.length - 1]?.sortValues;

  if (sortValues === undefined) {
    flowNodeInstanceStore.handleFetchFailure('sortValues not found');
    return;
  }

  const flowNodeInstances = await getFlowNodeInstances({
    treePath,
    pageSize: MAX_INSTANCES_PER_REQUEST,
    searchAfter: sortValues,
    processInstance,
  });

  if (flowNodeInstances === undefined) {
    return;
  }

  const subTree = flowNodeInstances[treePath];
  const subTreeChildren =
    flowNodeInstanceStore.state.flowNodeInstances[treePath]?.children;

  if (subTree === undefined || subTreeChildren === undefined) {
    flowNodeInstanceStore.handleFetchFailure(`subTree not found: ${treePath}`);
    return;
  }
  const fetchedInstancesCount = subTree.children.length;

  flowNodeInstances[treePath]!.children = [
    ...subTreeChildren,
    ...subTree.children,
  ].slice(-MAX_INSTANCES_STORED);

  flowNodeInstanceStore.handleFetchSuccess(flowNodeInstances);
  return fetchedInstancesCount;
};

const fetchPrevious = async (
  treePath: string,
  processInstance?: ProcessInstance,
) => {
  if (
    ['fetching-next', 'fetching-prev', 'fetching'].includes(
      flowNodeInstanceStore.state.status,
    )
  ) {
    return;
  }

  flowNodeInstanceStore.startFetchPrev();

  const children =
    flowNodeInstanceStore.state.flowNodeInstances[treePath]?.children;
  if (children === undefined) {
    return;
  }

  const sortValues = children && children[0]?.sortValues;

  if (sortValues === undefined) {
    flowNodeInstanceStore.handleFetchFailure('sortValues not found');
    return;
  }

  const flowNodeInstances = await getFlowNodeInstances({
    treePath,
    pageSize: MAX_INSTANCES_PER_REQUEST,
    searchBefore: sortValues,
    processInstance,
  });

  if (flowNodeInstances === undefined) {
    return;
  }

  const subTree = flowNodeInstances[treePath];
  const subTreeChildren =
    flowNodeInstanceStore.state.flowNodeInstances[treePath]?.children;

  if (subTree === undefined || subTreeChildren === undefined) {
    flowNodeInstanceStore.handleFetchFailure(`subTree not found: ${treePath}`);
    return;
  }

  const fetchedInstancesCount = subTree.children.length;

  flowNodeInstances[treePath]!.children = [
    ...subTree.children,
    ...subTreeChildren,
  ].slice(0, MAX_INSTANCES_STORED);

  flowNodeInstanceStore.handleFetchSuccess(flowNodeInstances);

  return fetchedInstancesCount;
};

const fetchSubTree = async ({
  treePath,
  searchAfter,
  processInstance,
}: {
  treePath: string;
  searchAfter?: FlowNodeInstance['sortValues'];
  processInstance?: ProcessInstance;
}) => {
  const flowNodeInstances = await getFlowNodeInstances({
    searchAfter,
    treePath,
    pageSize: MAX_INSTANCES_PER_REQUEST,
    processInstance,
  });
  if (flowNodeInstances !== undefined) {
    flowNodeInstanceStore.handleFetchSuccess(flowNodeInstances);
  }
};

const fetchInstanceExecutionHistory = (processInstance?: ProcessInstance) =>
  flowNodeInstanceStore.retryOnConnectionLost(
    async (processInstanceId: ProcessInstanceEntity['id']) => {
      flowNodeInstanceStore.startFetch();
      const flowNodeInstances = await getFlowNodeInstances({
        treePath: processInstanceId,
        pageSize: MAX_INSTANCES_PER_REQUEST,
        processInstance,
      });
      if (flowNodeInstances !== undefined) {
        flowNodeInstanceStore.handleFetchSuccess(flowNodeInstances);
      }
    },
  );

const getFlowNodeInstances = async ({
  treePath,
  pageSize,
  searchAfter,
  searchBefore,
  processInstance,
}: {
  treePath: string;
  pageSize?: number;
  searchAfter?: FlowNodeInstance['sortValues'];
  searchBefore?: FlowNodeInstance['sortValues'];
  processInstance?: ProcessInstance;
}): Promise<FlowNodeInstances | undefined> => {
  const processInstanceId = processInstance?.processInstanceKey;

  if (processInstanceId === undefined) {
    return;
  }

  flowNodeInstanceStore.stopPolling();

  const response = await fetchFlowNodeInstances([
    {
      processInstanceId: processInstanceId,
      treePath,
      pageSize,
      searchAfter,
      searchBefore,
    },
  ]);

  if (!response.isSuccess) {
    flowNodeInstanceStore.handleFetchFailure();
  }

  if (!modificationsStore.isModificationModeEnabled) {
    startPolling(processInstance);
  }

  return response.data;
};

export {
  init,
  pollInstances,
  startPolling,
  fetchNext,
  fetchPrevious,
  fetchSubTree,
  fetchInstanceExecutionHistory,
};
