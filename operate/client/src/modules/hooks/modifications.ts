/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {getFlowElementIds} from 'modules/bpmn-js/utils/getFlowElementIds';
import {isMultiInstance} from 'modules/bpmn-js/utils/isMultiInstance';
import {TOKEN_OPERATIONS} from 'modules/constants';
import {useElementInstancesStatistics} from 'modules/queries/elementInstancesStatistics/useElementInstancesStatistics';
import {
  useTotalRunningInstancesForElements,
  useTotalRunningInstancesVisibleForElements,
} from 'modules/queries/elementInstancesStatistics/useTotalRunningInstancesForElement';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {
  EMPTY_MODIFICATION,
  modificationsStore,
} from 'modules/stores/modifications';
import {useMemo} from 'react';
import {
  useAppendableElements,
  useCancellableElements,
  useNonModifiableElements,
} from './processInstanceDetailsDiagram';
import {isSubProcess} from 'modules/bpmn-js/utils/isSubProcess';
import {useHasPendingCancelOrMoveModification} from './elementSelection';

type ModificationOption =
  | 'add'
  | 'cancel-all'
  | 'cancel-instance'
  | 'move-all'
  | 'move-instance';

const useWillAllElementsBeCanceled = () => {
  const {data: statistics} = useElementInstancesStatistics();
  const modificationsByElement = useModificationsByElement();

  if (
    modificationsStore.elementModifications.some(
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
        modificationsByElement[elementId]?.areAllTokensCanceled,
    ) || false
  );
};

const useModificationsByElement = () => {
  const modificationElementIds = modificationsStore.elementModifications.map(
    (modification) => modification.element.id,
  );
  const {data: businessObjects} = useBusinessObjects();

  const {data: totalRunningInstancesForModificationElements} =
    useTotalRunningInstancesForElements(modificationElementIds);
  const totalRunningInstancesByModificationElements = useMemo(() => {
    return modificationElementIds.reduce(
      (acc, modificationElementId) => {
        acc[modificationElementId] =
          totalRunningInstancesForModificationElements?.[
            modificationElementId
          ] ?? 0;
        return acc;
      },
      {} as Record<string, number>,
    );
  }, [modificationElementIds, totalRunningInstancesForModificationElements]);

  const elementIds = modificationElementIds.flatMap((elementId) =>
    getFlowElementIds(businessObjects?.[elementId]),
  );

  const {data: elementCancelledTokens} =
    useTotalRunningInstancesForElements(elementIds);
  const {data: elementVisibleCancelledTokens} =
    useTotalRunningInstancesVisibleForElements(elementIds);

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

  return modificationsStore.elementModifications.reduce<{
    [key: string]: {
      newTokens: number;
      cancelledTokens: number;
      cancelledChildTokens: number;
      visibleCancelledTokens: number;
      areAllTokensCanceled: boolean;
    };
  }>((modificationsByElement, payload) => {
    const {element, operation, affectedTokenCount, visibleAffectedTokenCount} =
      payload;

    const sourceElement = modificationsByElement[element.id] ?? {
      ...EMPTY_MODIFICATION,
    };

    const totalRunningInstancesCount =
      totalRunningInstancesByModificationElements[element.id] ?? 0;

    if (operation === TOKEN_OPERATIONS.ADD_TOKEN) {
      sourceElement.newTokens += affectedTokenCount;
      modificationsByElement[element.id] = sourceElement;
      return modificationsByElement;
    }

    if (sourceElement.areAllTokensCanceled) {
      return modificationsByElement;
    }

    if (payload.elementInstanceKey === undefined) {
      sourceElement.cancelledTokens = affectedTokenCount;
      sourceElement.visibleCancelledTokens = visibleAffectedTokenCount;
    } else {
      sourceElement.cancelledTokens += affectedTokenCount;
      sourceElement.visibleCancelledTokens += visibleAffectedTokenCount;
    }

    sourceElement.areAllTokensCanceled =
      sourceElement.cancelledTokens === totalRunningInstancesCount;

    if (operation === TOKEN_OPERATIONS.MOVE_TOKEN) {
      const targetElement = modificationsByElement[
        payload.targetElement.id
      ] ?? {
        ...EMPTY_MODIFICATION,
      };

      targetElement.newTokens += isMultiInstance(businessObjects?.[element.id])
        ? 1
        : affectedTokenCount;

      modificationsByElement[payload.targetElement.id] = targetElement;
    }

    if (operation === TOKEN_OPERATIONS.CANCEL_TOKEN) {
      if (sourceElement.areAllTokensCanceled) {
        // set cancel token counts for child elements if element has any
        const elementIds = getFlowElementIds(businessObjects?.[element.id]);

        let affectedChildTokenCount = 0;
        elementIds.forEach((elementId) => {
          const childElement = modificationsByElement[elementId] ?? {
            ...EMPTY_MODIFICATION,
          };

          const {cancelledTokens, visibleCancelledTokens} =
            elementData[elementId] || {};

          if (cancelledTokens) {
            childElement.cancelledTokens = cancelledTokens;
          }
          if (visibleCancelledTokens) {
            childElement.visibleCancelledTokens = visibleCancelledTokens;
          }

          childElement.areAllTokensCanceled = true;

          affectedChildTokenCount += childElement.visibleCancelledTokens;

          modificationsByElement[elementId] = childElement;
        });

        sourceElement.cancelledChildTokens = affectedChildTokenCount;
      }
    }

    modificationsByElement[element.id] = sourceElement;
    return modificationsByElement;
  }, {});
};

const useNewScopeKeyForElement = (elementId: string | null) => {
  const modificationsByElement = useModificationsByElement();

  if (
    elementId === null ||
    (modificationsByElement[elementId]?.newTokens ?? 0) !== 1
  ) {
    return null;
  }

  const addTokenModification = modificationsStore.elementModifications.find(
    (modification) =>
      modification.operation === TOKEN_OPERATIONS.ADD_TOKEN &&
      modification.element.id === elementId,
  );

  if (addTokenModification !== undefined && 'scopeId' in addTokenModification) {
    return addTokenModification.scopeId;
  }

  const moveTokenModification = modificationsStore.elementModifications.find(
    (modification) =>
      modification.operation === TOKEN_OPERATIONS.MOVE_TOKEN &&
      modification.targetElement.id === elementId,
  );

  if (
    moveTokenModification !== undefined &&
    'scopeIds' in moveTokenModification
  ) {
    return moveTokenModification.scopeIds[0] ?? null;
  }

  return null;
};

const useCanBeCanceled = (
  runningElementInstanceCount: number,
  elementId?: string,
) => {
  const cancellableElements = useCancellableElements();
  const canBeModified = useCanBeModified(elementId);
  const hasPendingCancelOrMoveModification =
    useHasPendingCancelOrMoveModification();

  if (elementId === undefined || !canBeModified) {
    return false;
  }

  return (
    cancellableElements.includes(elementId) &&
    !hasPendingCancelOrMoveModification &&
    runningElementInstanceCount > 0
  );
};

const useCanBeModified = (elementId?: string) => {
  const nonModifiableElements = useNonModifiableElements();

  if (elementId === undefined) {
    return false;
  }

  return !nonModifiableElements.includes(elementId);
};

type UseAvailableModificationsParams = {
  runningElementInstanceCount: number;
  elementId?: string;
  isSpecificElementInstanceSelected?: boolean;
  isMultiInstanceBody?: boolean;
  isSingleRunningInstanceResolved?: boolean;
};

const useAvailableModifications = ({
  runningElementInstanceCount,
  elementId,
  isSpecificElementInstanceSelected,
  isMultiInstanceBody,
  isSingleRunningInstanceResolved,
}: UseAvailableModificationsParams) => {
  const options: ModificationOption[] = [];
  const appendableElements = useAppendableElements();
  const {data: businessObjects} = useBusinessObjects();
  const canBeCanceled = useCanBeCanceled(
    runningElementInstanceCount,
    elementId,
  );
  const canBeModified = useCanBeModified(elementId);

  if (elementId === undefined || !canBeModified) {
    return options;
  }

  if (
    appendableElements.includes(elementId) &&
    !(isMultiInstance(businessObjects?.[elementId]) && !isMultiInstanceBody) &&
    !isSpecificElementInstanceSelected
  ) {
    options.push('add');
  }

  if (!canBeCanceled) {
    return options;
  }

  const isSingleOperationAllowed =
    isSingleRunningInstanceResolved &&
    runningElementInstanceCount === 1 &&
    !isSubProcess(businessObjects?.[elementId]);

  if (isSingleOperationAllowed) {
    options.push('cancel-instance');
  } else {
    options.push('cancel-all');
  }

  if (isSubProcess(businessObjects?.[elementId])) {
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
  useModificationsByElement,
  useNewScopeKeyForElement,
  useWillAllElementsBeCanceled,
};
