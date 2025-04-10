/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useProcessDefinitionKeyContext} from 'App/Processes/ListView/processDefinitionKeyContext';
import {getFlowElementIds} from 'modules/bpmn-js/utils/getFlowElementIds';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {
  useTotalRunningInstancesForFlowNodes,
  useTotalRunningInstancesVisibleForFlowNodes,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {useProcessInstanceXml} from 'modules/queries/processDefinitions/useProcessInstanceXml';
import {
  EMPTY_MODIFICATION,
  modificationsStore,
} from 'modules/stores/modifications';
import {useMemo} from 'react';

const useWillAllFlowNodesBeCanceled = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();

  if (
    modificationsStore.flowNodeModifications.some(
      ({operation}) =>
        operation === TOKEN_OPERATIONS.ADD_TOKEN ||
        operation === TOKEN_OPERATIONS.MOVE_TOKEN,
    )
  ) {
    return false;
  }

  return (
    statistics?.items.every(
      ({flowNodeId, active, incidents}) =>
        (active === 0 && incidents === 0) ||
        modificationsStore.modificationsByFlowNode[flowNodeId]
          ?.areAllTokensCanceled,
    ) || false
  );
};

const useModificationsByFlowNode = () => {
  const flowNodeIds = modificationsStore.flowNodeModifications.map(
    (modification) => modification.flowNode.id,
  );
  const processDefinitionKey = useProcessDefinitionKeyContext();
  const businessObjects =
    useProcessInstanceXml({
      processDefinitionKey: processDefinitionKey,
    }).data?.businessObjects ?? {};

  const {data: flowNodeDataArray} =
    useTotalRunningInstancesForFlowNodes(flowNodeIds);
  const flowNodeData = useMemo(() => {
    return flowNodeIds.reduce(
      (acc, id, index) => {
        acc[id] = flowNodeDataArray?.[index] ?? 0;
        return acc;
      },
      {} as Record<string, number>,
    );
  }, [flowNodeIds, flowNodeDataArray]);

  const elementIds = flowNodeIds.flatMap((flowNodeId) =>
    getFlowElementIds(businessObjects[flowNodeId]),
  );

  const {data: elementCancelledTokens} =
    useTotalRunningInstancesForFlowNodes(elementIds);
  const {data: elementVisibleCancelledTokens} =
    useTotalRunningInstancesVisibleForFlowNodes(elementIds);

  const elementData = useMemo(() => {
    return elementIds.reduce(
      (acc, id, index) => {
        acc[id] = {
          cancelledTokens: elementCancelledTokens?.[index] ?? 0,
          visibleCancelledTokens: elementVisibleCancelledTokens?.[index] ?? 0,
        };
        return acc;
      },
      {} as Record<
        string,
        {cancelledTokens: number; visibleCancelledTokens: number}
      >,
    );
  }, [elementIds, elementCancelledTokens, elementVisibleCancelledTokens]);

  return modificationsStore.flowNodeModifications.reduce<{
    [key: string]: {
      newTokens: number;
      cancelledTokens: number;
      cancelledChildTokens: number;
      visibleCancelledTokens: number;
      areAllTokensCanceled: boolean;
    };
  }>((modificationsByFlowNode, payload) => {
    const {flowNode, operation, affectedTokenCount, visibleAffectedTokenCount} =
      payload;

    const sourceFlowNode = modificationsByFlowNode[flowNode.id] ?? {
      ...EMPTY_MODIFICATION,
    };

    const totalRunningInstancesCount = flowNodeData[flowNode.id] ?? 0;

    if (operation === TOKEN_OPERATIONS.ADD_TOKEN) {
      sourceFlowNode.newTokens += affectedTokenCount;
      modificationsByFlowNode[flowNode.id] = sourceFlowNode;
      return modificationsByFlowNode;
    }

    if (sourceFlowNode.areAllTokensCanceled) {
      return modificationsByFlowNode;
    }

    if (payload.flowNodeInstanceKey === undefined) {
      sourceFlowNode.cancelledTokens = affectedTokenCount;
      sourceFlowNode.visibleCancelledTokens = visibleAffectedTokenCount;
    } else {
      sourceFlowNode.cancelledTokens += affectedTokenCount;
      sourceFlowNode.visibleCancelledTokens += visibleAffectedTokenCount;
    }

    sourceFlowNode.areAllTokensCanceled =
      sourceFlowNode.cancelledTokens === totalRunningInstancesCount;

    if (operation === TOKEN_OPERATIONS.MOVE_TOKEN) {
      const targetFlowNode = modificationsByFlowNode[
        payload.targetFlowNode.id
      ] ?? {
        ...EMPTY_MODIFICATION,
      };

      targetFlowNode.newTokens += isMultiInstance(businessObjects[flowNode.id])
        ? 1
        : affectedTokenCount;

      modificationsByFlowNode[payload.targetFlowNode.id] = targetFlowNode;
    }

    if (operation === TOKEN_OPERATIONS.CANCEL_TOKEN) {
      if (sourceFlowNode.areAllTokensCanceled) {
        // set cancel token counts for child elements if flow node has any
        const elementIds = getFlowElementIds(businessObjects?.[flowNode.id]);

        let affectedChildTokenCount = 0;
        elementIds.forEach((elementId) => {
          const childFlowNode = modificationsByFlowNode[elementId] ?? {
            ...EMPTY_MODIFICATION,
          };

          const {cancelledTokens, visibleCancelledTokens} =
            elementData[elementId] || {};

          if (cancelledTokens) {
            childFlowNode.cancelledTokens = cancelledTokens;
          }
          if (visibleCancelledTokens) {
            childFlowNode.visibleCancelledTokens = visibleCancelledTokens;
          }

          childFlowNode.areAllTokensCanceled = true;

          affectedChildTokenCount += childFlowNode.visibleCancelledTokens;

          modificationsByFlowNode[elementId] = childFlowNode;
        });

        sourceFlowNode.cancelledChildTokens = affectedChildTokenCount;
      }
    }

    modificationsByFlowNode[flowNode.id] = sourceFlowNode;
    return modificationsByFlowNode;
  }, {});
};

export {useModificationsByFlowNode, useWillAllFlowNodesBeCanceled};
