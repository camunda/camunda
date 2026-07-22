/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, BusinessObjects, ElementType} from 'bpmn-js/lib/NavigatedViewer';
import type {DiagramModel} from 'bpmn-moddle';
import type {OverlayData} from '#/operate/shared/Diagram/overlayTypes';
import {SUBPROCESS_WITH_INCIDENTS_BADGE} from './badgePositions';

function hasType({businessObject, types}: {businessObject: BusinessObject; types: ElementType[]}) {
	return types.includes(businessObject.$type);
}

function isFlowNode(businessObject: BusinessObject) {
	return businessObject.$instanceOf?.('bpmn:FlowNode') ?? false;
}

function isProcessOrSubProcessEndEvent(businessObject: BusinessObject) {
	return (
		hasType({businessObject, types: ['bpmn:EndEvent']}) &&
		businessObject.$parent !== undefined &&
		hasType({businessObject: businessObject.$parent, types: ['bpmn:Process', 'bpmn:SubProcess']})
	);
}

function getFlowNodes(elementsById?: DiagramModel['elementsById']) {
	if (elementsById === undefined) {
		return [];
	}

	return Object.values(elementsById).filter(isFlowNode);
}

function getBusinessObjects(elementsById?: DiagramModel['elementsById']): BusinessObjects {
	return getFlowNodes(elementsById).reduce<BusinessObjects>((flowNodes, businessObject) => {
		flowNodes[businessObject.id] = businessObject;
		return flowNodes;
	}, {});
}

function getSubprocessOverlayFromIncidentElements(
	flowNodes: (BusinessObject | undefined)[],
	type: string,
): OverlayData[] {
	const overlays: OverlayData[] = [];

	flowNodes.forEach((flowNode) => {
		let current = flowNode;
		while (current?.$parent) {
			const parent = current.$parent;
			if (parent.$type === 'bpmn:SubProcess') {
				overlays.push({
					payload: {elementState: 'incidents'},
					type,
					elementId: parent.id,
					position: SUBPROCESS_WITH_INCIDENTS_BADGE,
				});
			}
			current = parent;
		}
	});

	return overlays;
}

export {
	hasType,
	isFlowNode,
	isProcessOrSubProcessEndEvent,
	getFlowNodes,
	getBusinessObjects,
	getSubprocessOverlayFromIncidentElements,
};
