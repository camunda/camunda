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
  SubprocessOverlay,
} from 'bpmn-js/lib/NavigatedViewer';
import type {DiagramModel} from 'bpmn-moddle';
import {SUBPROCESS_WITH_INCIDENTS} from 'modules/bpmn-js/badgePositions';

function isFlowNode(businessObject: BusinessObject) {
  return businessObject.$instanceOf?.('bpmn:FlowNode') ?? false;
}

function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter((businessObject) =>
    isFlowNode(businessObject),
  );
}

function getElement({
  businessObjects,
  elementId,
}: {
  businessObjects?: BusinessObjects;
  elementId?: string;
}) {
  return elementId ? businessObjects?.[elementId] : undefined;
}

function getElementName({
  businessObjects,
  elementId,
}: {
  businessObjects?: BusinessObjects;
  elementId?: string;
}) {
  return getElement({businessObjects, elementId})?.name ?? elementId ?? '';
}

function getSubprocessOverlayFromIncidentElements(
  flowNodes?: (BusinessObject | undefined)[],
  type: string = 'elementState',
) {
  const overlays: SubprocessOverlay[] = [];

  flowNodes?.forEach((flowNode) => {
    while (flowNode?.$parent) {
      const parent = flowNode.$parent;
      if (parent.$type === 'bpmn:SubProcess') {
        overlays.push({
          payload: {elementState: 'incidents'},
          type: type,
          elementId: parent.id,
          position: SUBPROCESS_WITH_INCIDENTS,
        });
      }
      flowNode = parent;
    }
  });

  return overlays;
}

export {
  isFlowNode,
  getFlowNodes,
  getElement,
  getElementName,
  getSubprocessOverlayFromIncidentElements,
};
