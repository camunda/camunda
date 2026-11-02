/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {anchorStateOverlaysToCorners} from './anchorStateOverlaysToCorners';

describe('anchorStateOverlaysToCorners', () => {
	it.for([
		{position: {bottom: 9, left: 0}, expected: {bottom: 0, left: 0}},
		{position: {bottom: 9, right: 0}, expected: {bottom: 0, right: 0}},
		{position: {top: -16, left: 0}, expected: {top: 0, left: 0}},
		{position: {top: -16, right: 0}, expected: {top: 0, right: 0}},
	])('should anchor the statistic at $position to the element corner', ({position, expected}) => {
		const [overlay] = anchorStateOverlaysToCorners([
			{type: 'instance-state', elementId: 'task', position, payload: {elementState: 'active', count: 1}},
		]);

		expect(overlay?.position).toEqual(expected);
	});

	it.for([
		{type: 'instance-state', payload: {elementState: 'completedEndEvents', count: 1}},
		{type: 'instance-state', payload: {elementState: 'active'}},
		{type: 'instance-waiting', payload: {waitingCount: 1}},
	])('should keep the position of the $type overlay with payload $payload', ({type, payload}) => {
		const position = {bottom: 1, left: 17};

		const [overlay] = anchorStateOverlaysToCorners([{type, elementId: 'task', position, payload}]);

		expect(overlay?.position).toBe(position);
	});
});
