/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {DiagramModel} from 'bpmn-moddle';
import {SUBPROCESS_WITH_INCIDENTS} from 'modules/bpmn-js/badgePositions';
import {SubprocessOverlay} from 'modules/stores/processStatistics/processStatistics.base';

export function isFlowNode(businessObject: BusinessObject) {
  return businessObject.$instanceOf?.('bpmn:FlowNode') ?? false;
}

export function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
  if (elementsById === undefined) {
    return [];
  }

  return Object.values(elementsById).filter((businessObject) =>
    isFlowNode(businessObject),
  );
}

export function getSubprocessOverlayFromIncidentFlowNodes(
  flowNodes?: (BusinessObject | undefined)[],
  type: string = 'flowNodeState',
) {
  const overlays: SubprocessOverlay[] = [];

  flowNodes?.forEach((flowNode) => {
    while (flowNode?.$parent) {
      const parent = flowNode.$parent;
      if (parent.$type === 'bpmn:SubProcess') {
        overlays.push({
          payload: {flowNodeState: 'incidents'},
          type: type,
          flowNodeId: parent.id,
          position: SUBPROCESS_WITH_INCIDENTS,
        });
      }
      flowNode = parent;
    }
  });

  return overlays;
}
