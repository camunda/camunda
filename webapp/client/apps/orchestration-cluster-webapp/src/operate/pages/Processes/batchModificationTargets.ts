/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';

const UNSUPPORTED_TYPES = ['bpmn:StartEvent', 'bpmn:BoundaryEvent'];

function isAttachedToEventBasedGateway(element: BusinessObject) {
	return element.incoming?.some((flow) => flow.sourceRef?.$type === 'bpmn:EventBasedGateway') ?? false;
}

function isMoveTarget(element: BusinessObject | undefined): element is BusinessObject {
	return element !== undefined && !UNSUPPORTED_TYPES.includes(element.$type) && !isAttachedToEventBasedGateway(element);
}

function getMoveSourceRestriction(element: BusinessObject | undefined) {
	if (element === undefined) {
		return 'selectElement';
	}
	if (
		UNSUPPORTED_TYPES.includes(element.$type) ||
		element.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics'
	) {
		return 'unsupportedType';
	}
	return element.$parent?.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics'
		? 'insideMultiInstance'
		: null;
}

export {isMoveTarget, getMoveSourceRestriction, isAttachedToEventBasedGateway};
