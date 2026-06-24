/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {
  BusinessObject,
  BusinessObjects,
} from 'bpmn-js/lib/NavigatedViewer';
import {
  instanceHistoryModificationStore,
  type ModificationPlaceholder,
} from 'modules/stores/instanceHistoryModification';

import {
  modificationsStore,
  type ElementModification,
} from 'modules/stores/modifications';

const getScopeIds = (modificationPayload: ElementModification['payload']) => {
  const {operation} = modificationPayload;

  switch (operation) {
    case 'ADD_TOKEN':
      return [modificationPayload.scopeId];
    case 'MOVE_TOKEN':
      return modificationPayload.scopeIds;
    default:
      return [];
  }
};

const generateParentPlaceholders = (
  modificationPayload: ElementModification['payload'],
  processDefinitionId?: string,
  processInstanceKey?: string,
  element?: BusinessObject,
): ModificationPlaceholder[] => {
  if (
    element === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return [];
  }

  const scopeId = modificationPayload.parentScopeIds[element.id];
  if (scopeId === undefined) {
    return [];
  }

  const parentElement = element?.$parent;

  return [
    ...generateParentPlaceholders(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      parentElement,
    ),
    {
      elementInstancePlaceholder: {
        elementId: element.id,
        elementInstanceKey: scopeId,
        isPlaceholder: true,
      },
      operation: modificationPayload.operation,
      parentElementId: parentElement?.id,
      parentInstanceId: getParentInstanceIdForPlaceholder(
        modificationPayload,
        processDefinitionId,
        processInstanceKey,
        parentElement?.id,
      ),
    },
  ];
};

const getParentInstanceIdForPlaceholder = (
  modificationPayload: ElementModification['payload'],
  processDefinitionId?: string,
  processInstanceKey?: string,
  parentElementId?: string,
) => {
  if (
    parentElementId === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return undefined;
  }

  if (parentElementId === processDefinitionId) {
    return processInstanceKey;
  }

  if (
    'ancestorElement' in modificationPayload &&
    modificationPayload.ancestorElement !== undefined &&
    parentElementId === modificationPayload.ancestorElement.elementId
  ) {
    return modificationPayload.ancestorElement.instanceKey;
  }

  return (
    modificationPayload.parentScopeIds[parentElementId] ??
    modificationsStore.getParentScopeId(parentElementId)
  );
};

const createModificationPlaceholders = ({
  modificationPayload,
  element,
  processDefinitionId,
  processInstanceKey,
}: {
  modificationPayload: ElementModification['payload'];
  element: BusinessObject;
  processDefinitionId?: string;
  processInstanceKey?: string;
}): ModificationPlaceholder[] => {
  const parentElementId = element.$parent?.id;

  if (
    parentElementId === undefined ||
    modificationPayload.operation === 'CANCEL_TOKEN'
  ) {
    return [];
  }

  return getScopeIds(modificationPayload).map((scopeId) => ({
    elementInstancePlaceholder: {
      elementId: element.id,
      elementInstanceKey: scopeId,
      isPlaceholder: true,
    },
    operation: modificationPayload.operation,
    parentElementId,
    parentInstanceId: getParentInstanceIdForPlaceholder(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      parentElementId,
    ),
  }));
};

const getModificationPlaceholders = (
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
) => {
  return modificationsStore.elementModifications.reduce<
    ModificationPlaceholder[]
  >((modificationPlaceHolders, modificationPayload) => {
    const {operation} = modificationPayload;
    if (operation === 'CANCEL_TOKEN') {
      return modificationPlaceHolders;
    }

    const elementId =
      operation === 'MOVE_TOKEN'
        ? modificationPayload.targetElement.id
        : modificationPayload.element.id;

    const element = businessObjects[elementId];

    if (element === undefined) {
      return modificationPlaceHolders;
    }

    const newParentModificationPlaceholders = generateParentPlaceholders(
      modificationPayload,
      processDefinitionId,
      processInstanceKey,
      element.$parent,
    );

    const newModificationPlaceholders = createModificationPlaceholders({
      modificationPayload,
      element,
    });

    return [
      ...modificationPlaceHolders,
      ...newModificationPlaceholders,
      ...newParentModificationPlaceholders,
    ];
  }, []);
};

const getVisibleChildPlaceholders = (
  scopeKey: string,
  elementId: string,
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
  isPlaceholder?: boolean,
) => {
  if (
    isPlaceholder &&
    !instanceHistoryModificationStore.state.expandedElementInstanceIds.includes(
      scopeKey,
    )
  ) {
    return [];
  }

  const placeholders = getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  )
    .filter(({parentInstanceId}) => parentInstanceId === scopeKey)
    .map(({elementInstancePlaceholder}) => elementInstancePlaceholder);

  if (placeholders.length > 0) {
    return placeholders;
  }

  return getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  )
    .filter(
      ({parentInstanceId, parentElementId}) =>
        parentInstanceId === undefined && parentElementId === elementId,
    )
    .map(({elementInstancePlaceholder}) => elementInstancePlaceholder);
};

const hasChildPlaceholders = (
  scopeKey: string,
  businessObjects: BusinessObjects,
  processDefinitionId?: string,
  processInstanceKey?: string,
) => {
  return getModificationPlaceholders(
    businessObjects,
    processDefinitionId,
    processInstanceKey,
  ).some(({parentInstanceId}) => parentInstanceId === scopeKey);
};

export {hasChildPlaceholders, getVisibleChildPlaceholders};
