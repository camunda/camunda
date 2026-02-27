/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {isMoveModificationTarget} from 'modules/bpmn-js/utils/isMoveModificationTarget';
import {getFirstMultiInstanceParent} from 'modules/bpmn-js/utils/isWithinMultiInstance';
import {useElementInstancesStatistics} from 'modules/queries/elementInstancesStatistics/useElementInstancesStatistics';
import {useTotalRunningInstancesByElement} from 'modules/queries/elementInstancesStatistics/useTotalRunningInstancesForElement';
import {useBusinessObjects} from 'modules/queries/processDefinitions/useBusinessObjects';
import {modificationsStore} from 'modules/stores/modifications';
import {
  hasMultipleScopes,
  hasSingleScope,
} from 'modules/utils/processInstanceDetailsDiagram';

const useElements = () => {
  const {data: statistics} = useElementInstancesStatistics();
  const {data: totalRunningInstancesByElement} =
    useTotalRunningInstancesByElement();
  const {data: businessObjects} = useBusinessObjects();

  return Object.values(businessObjects ?? {}).map((element) => {
    const firstMultiInstanceParent = getFirstMultiInstanceParent(element);
    const elementState = statistics?.items.find(
      ({elementId}) => elementId === element.id,
    );

    return {
      id: element.id,
      isCancellable:
        elementState !== undefined &&
        (elementState.active > 0 || elementState.incidents > 0),
      isMoveModificationTarget: isMoveModificationTarget(element),
      firstMultiInstanceParent,
      hasMultipleScopes: hasMultipleScopes(
        element.$parent,
        totalRunningInstancesByElement,
      ),
      hasSingleScope:
        !firstMultiInstanceParent ||
        hasSingleScope(element.$parent, totalRunningInstancesByElement),
    };
  });
};

const useAppendableElements = () => {
  const elements = useElements();
  const {
    state: {status, sourceElementIdForMoveOperation},
    isMoveAllOperation,
  } = modificationsStore;

  const sourceMultiInstanceParent = elements.find(
    ({id}) => id === sourceElementIdForMoveOperation,
  )?.firstMultiInstanceParent;

  return elements
    .filter((element) => {
      if (!element.isMoveModificationTarget) {
        return false;
      }

      // Add token
      if (status !== 'moving-token') {
        return element.hasSingleScope && !element.hasMultipleScopes;
      }

      // Moving token is allowed for 1 scope
      if (element.hasSingleScope) {
        return true;
      }

      // Moving multiple tokens to another element inside the multi-instance is not allowed
      if (isMoveAllOperation) {
        return false;
      }

      // Moving to/from different multi-instance parents is not allowed
      if (sourceMultiInstanceParent !== element.firstMultiInstanceParent) {
        return false;
      }

      return true;
    })
    .map(({id}) => id);
};

const useCancellableElements = () => {
  return useElements()
    .filter((element) => element.isCancellable)
    .map(({id}) => id);
};

const useModifiableElements = () => {
  const appendableElements = useAppendableElements();
  const cancellableElements = useCancellableElements();

  if (modificationsStore.state.status === 'moving-token') {
    return appendableElements.filter(
      (elementId) =>
        elementId !== modificationsStore.state.sourceElementIdForMoveOperation,
    );
  } else {
    return Array.from(new Set([...appendableElements, ...cancellableElements]));
  }
};

const useNonModifiableElements = () => {
  const elements = useElements();
  const modifiableElements = useModifiableElements();

  return elements
    .filter((element) => !modifiableElements.includes(element.id))
    .map(({id}) => id);
};

export {
  useAppendableElements,
  useCancellableElements,
  useModifiableElements,
  useNonModifiableElements,
};
