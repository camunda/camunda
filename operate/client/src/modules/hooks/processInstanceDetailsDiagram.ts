/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
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

const useElementBusinessObjects = () => {
  const {data} = useBusinessObjects();
  return Object.values(data ?? {});
};

function useScopeProperties() {
  const {data: totalRunningInstancesByElement} =
    useTotalRunningInstancesByElement();

  return {
    hasMultipleScopes: (element: BusinessObject) =>
      hasMultipleScopes(element?.$parent, totalRunningInstancesByElement),
    hasEffectiveSingleScope: (element: BusinessObject) =>
      !getFirstMultiInstanceParent(element) ||
      hasSingleScope(element?.$parent, totalRunningInstancesByElement),
  };
}

const useAppendableElements = () => {
  const {
    state: {status, sourceFlowNodeIdForMoveOperation},
    isMoveAllOperation,
  } = modificationsStore;

  const elements = useElementBusinessObjects();
  const {hasEffectiveSingleScope, hasMultipleScopes} = useScopeProperties();
  const sourceElement = elements.find(
    ({id}) => id === sourceFlowNodeIdForMoveOperation,
  );

  return elements
    .filter((element) => {
      if (!isMoveModificationTarget(element)) {
        return false;
      }

      // Add token
      if (status !== 'moving-token') {
        return hasEffectiveSingleScope(element) && !hasMultipleScopes(element);
      }

      // Moving token is allowed for 1 scope
      if (hasEffectiveSingleScope(element)) {
        return true;
      }

      // Moving multiple tokens to another element inside the multi-instance is not allowed
      if (isMoveAllOperation) {
        return false;
      }

      // Moving to/from different multi-instance parents is not allowed
      if (
        getFirstMultiInstanceParent(sourceElement) !==
        getFirstMultiInstanceParent(element)
      ) {
        return false;
      }

      return true;
    })
    .map(({id}) => id);
};

const useCancellableElements = () => {
  const elements = useElementBusinessObjects();
  const {data: statistics} = useElementInstancesStatistics();
  const elementState = (element: BusinessObject) =>
    statistics?.items.find(({elementId}) => elementId === element.id);

  return elements
    .filter((element) => {
      const state = elementState(element);
      if (!state) {
        return false;
      }

      return state.active > 0 || state.incidents > 0;
    })
    .map(({id}) => id);
};

const useModifiableElements = () => {
  const appendableElements = useAppendableElements();
  const cancellableElements = useCancellableElements();

  if (modificationsStore.state.status === 'moving-token') {
    return appendableElements.filter(
      (elementId) =>
        elementId !== modificationsStore.state.sourceFlowNodeIdForMoveOperation,
    );
  } else {
    return Array.from(new Set([...appendableElements, ...cancellableElements]));
  }
};

const useNonModifiableElements = () => {
  const elements = useElementBusinessObjects();
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
