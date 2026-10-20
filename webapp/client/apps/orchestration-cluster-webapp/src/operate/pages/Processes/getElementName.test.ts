/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {it} from '#/vitest-modules/test-extend';
import {getElementName} from './getElementName';

const businessObjectsWithName = (name: string | undefined) =>
	({task: {id: 'task', $type: 'bpmn:ServiceTask', name}}) as unknown as BusinessObjects;

describe('getElementName', () => {
	it.for([
		{description: 'the element name', name: 'Check order', expected: 'Check order'},
		{description: 'the element id when the name is missing', name: undefined, expected: 'task'},
		{description: 'an empty name as it is, like legacy', name: '', expected: ''},
		{description: 'a blank name as it is, like legacy', name: '   ', expected: '   '},
	])('should return $description', ({name, expected}) => {
		expect(getElementName({businessObjects: businessObjectsWithName(name), elementId: 'task'})).toBe(expected);
	});
});
