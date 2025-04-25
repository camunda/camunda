/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import isNil from 'lodash/isNil';
import {getFlowElementIds} from 'modules/bpmn-js/utils/getFlowElementIds';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {useFlownodeInstancesStatistics} from 'modules/queries/flownodeInstancesStatistics/useFlownodeInstancesStatistics';
import {
  useTotalRunningInstancesForFlowNodes,
  useTotalRunningInstancesVisibleForFlowNodes,
} from 'modules/queries/flownodeInstancesStatistics/useTotalRunningInstancesForFlowNode';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {flowNodeSelectionStore} from 'modules/stores/flowNodeSelection';
import {
  EMPTY_MODIFICATION,
  modificationsStore,
} from 'modules/stores/modifications';
import {useMemo} from 'react';
import {
  useAppendableFlowNodes,
  useCancellableFlowNodes,
  useNonModifiableFlowNodes,
} from './processInstanceDetailsDiagram';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {useHasPendingCancelOrMoveModification} from './flowNodeSelection';

type ModificationOption =
  | 'add'
  | 'cancel-all'
  | 'cancel-instance'
  | 'move-all'
  | 'move-instance';

const useWillAllFlowNodesBeCanceled = () => {
  const {data: statistics} = useFlownodeInstancesStatistics();
  const modificationsByFlowNode = useModificationsByFlowNode();

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
      ({elementId, active, incidents}) =>
        (active === 0 && incidents === 0) ||
        modificationsByFlowNode[elementId]?.areAllTokensCanceled,
    ) || false
  );
};

const useModificationsByFlowNode = () => {
  const flowNodeIds = modificationsStore.flowNodeModifications.map(
    (modification) => modification.flowNode.id,
  );
  const {data: businessObjects} = useBusinessObjects();

  const {data: flowNodeDataArray} =
    useTotalRunningInstancesForFlowNodes(flowNodeIds);
  const flowNodeData = useMemo(() => {
    return flowNodeIds.reduce(
      (acc, id) => {
        acc[id] = flowNodeDataArray?.[id] ?? 0;
        return acc;
      },
      {} as Record<string, number>,
    );
  }, [flowNodeIds, flowNodeDataArray]);

  const elementIds = flowNodeIds.flatMap((elementId) =>
    getFlowElementIds(businessObjects?.[elementId]),
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

      targetFlowNode.newTokens += isMultiInstance(
        businessObjects?.[flowNode.id],
      )
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

const useNewScopeIdForFlowNode = (elementId?: string) => {
  const modificationsByFlowNode = useModificationsByFlowNode();

  if (
    elementId === undefined ||
    (modificationsByFlowNode[elementId]?.newTokens ?? 0) !== 1
  ) {
    return null;
  }

  const addTokenModification = modificationsStore.flowNodeModifications.find(
    (modification) =>
      modification.operation === TOKEN_OPERATIONS.ADD_TOKEN &&
      modification.flowNode.id === elementId,
  );

  if (addTokenModification !== undefined && 'scopeId' in addTokenModification) {
    return addTokenModification.scopeId;
  }

  const moveTokenModification = modificationsStore.flowNodeModifications.find(
    (modification) =>
      modification.operation === TOKEN_OPERATIONS.MOVE_TOKEN &&
      modification.targetFlowNode.id === elementId,
  );

  if (
    moveTokenModification !== undefined &&
    'scopeIds' in moveTokenModification
  ) {
    return moveTokenModification.scopeIds[0] ?? null;
  }

  return null;
};

const useCanBeCanceled = (selectedRunningInstanceCount: number) => {
  const cancellableFlowNodes = useCancellableFlowNodes();
  const canBeModified = useCanBeModified();
  const hasPendingCancelOrMoveModification =
    useHasPendingCancelOrMoveModification();

  if (
    flowNodeSelectionStore.selectedFlowNodeId === undefined ||
    !canBeModified
  ) {
    return false;
  }

  return (
    cancellableFlowNodes.includes(flowNodeSelectionStore.selectedFlowNodeId) &&
    !hasPendingCancelOrMoveModification &&
    selectedRunningInstanceCount > 0
  );
};

const useCanBeModified = () => {
  const nonModifiableFlowNodes = useNonModifiableFlowNodes();

  if (flowNodeSelectionStore.selectedFlowNodeId === undefined) {
    return false;
  }

  return !nonModifiableFlowNodes.includes(
    flowNodeSelectionStore.selectedFlowNodeId,
  );
};

const useAvailableModifications = (selectedRunningInstanceCount: number) => {
  const options: ModificationOption[] = [];
  const {
    state: {selection},
  } = flowNodeSelectionStore;
  const appendableFlowNodes = useAppendableFlowNodes();
  const {data: businessObjects} = useBusinessObjects();
  const canBeCanceled = useCanBeCanceled(selectedRunningInstanceCount);
  const canBeModified = useCanBeModified();

  if (
    flowNodeSelectionStore.selectedFlowNodeId === undefined ||
    !canBeModified
  ) {
    return options;
  }

  if (
    appendableFlowNodes.includes(flowNodeSelectionStore.selectedFlowNodeId) &&
    !(
      isMultiInstance(
        businessObjects?.[flowNodeSelectionStore.selectedFlowNodeId],
      ) && !selection?.isMultiInstance
    ) &&
    selection?.flowNodeInstanceId === undefined
  ) {
    options.push('add');
  }

  if (!canBeCanceled) {
    return options;
  }

  const isSingleOperationAllowed =
    !isNil(flowNodeSelectionStore.selectedFlowNodeInstanceId) &&
    selectedRunningInstanceCount === 1 &&
    !isSubProcess(businessObjects?.[flowNodeSelectionStore.selectedFlowNodeId]);

  if (isSingleOperationAllowed) {
    options.push('cancel-instance');
  } else {
    options.push('cancel-all');
  }

  if (
    isSubProcess(businessObjects?.[flowNodeSelectionStore.selectedFlowNodeId])
  ) {
    return options;
  }

  if (isSingleOperationAllowed) {
    options.push('move-instance');
  } else {
    options.push('move-all');
  }

  return options;
};

export {
  useAvailableModifications,
  useCanBeModified,
  useModificationsByFlowNode,
  useNewScopeIdForFlowNode,
  useWillAllFlowNodesBeCanceled,
};
