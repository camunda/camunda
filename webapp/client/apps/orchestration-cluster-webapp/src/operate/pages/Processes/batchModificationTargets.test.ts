/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {it} from '#/vitest-modules/test-extend';
import {getMoveSourceRestriction, isAttachedToEventBasedGateway, isMoveTarget} from './batchModificationTargets';

const multiInstance = {$type: 'bpmn:MultiInstanceLoopCharacteristics'};
const element = (overrides: Record<string, unknown>) =>
	({id: 'element', $type: 'bpmn:ServiceTask', ...overrides}) as BusinessObject;
const afterEventGateway = element({
	$type: 'bpmn:IntermediateCatchEvent',
	incoming: [{sourceRef: {$type: 'bpmn:EventBasedGateway'}}],
});

describe('batchModificationTargets', () => {
	it.for([
		{description: 'a missing element', source: undefined, restriction: 'selectElement'},
		{description: 'a start event', source: element({$type: 'bpmn:StartEvent'}), restriction: 'unsupportedType'},
		{description: 'a boundary event', source: element({$type: 'bpmn:BoundaryEvent'}), restriction: 'unsupportedType'},
		{
			description: 'a multi instance element',
			source: element({loopCharacteristics: multiInstance}),
			restriction: 'unsupportedType',
		},
		{
			description: 'an element inside a multi instance',
			source: element({$parent: {loopCharacteristics: multiInstance}}),
			restriction: 'insideMultiInstance',
		},
		{description: 'a service task', source: element({}), restriction: null},
	])('should restrict moving from $description', ({source, restriction}) => {
		expect(getMoveSourceRestriction(source)).toBe(restriction);
	});

	it('should detect an element attached to an event based gateway', () => {
		expect(isAttachedToEventBasedGateway(afterEventGateway)).toBe(true);
		expect(isAttachedToEventBasedGateway(element({}))).toBe(false);
	});

	it.for([
		{description: 'a missing element', target: undefined, isTarget: false},
		{description: 'a start event', target: element({$type: 'bpmn:StartEvent'}), isTarget: false},
		{description: 'a boundary event', target: element({$type: 'bpmn:BoundaryEvent'}), isTarget: false},
		{description: 'an element after an event based gateway', target: afterEventGateway, isTarget: false},
		{description: 'an end event', target: element({$type: 'bpmn:EndEvent'}), isTarget: true},
	])('should decide whether $description is a move target', ({target, isTarget}) => {
		expect(isMoveTarget(target)).toBe(isTarget);
	});
});
