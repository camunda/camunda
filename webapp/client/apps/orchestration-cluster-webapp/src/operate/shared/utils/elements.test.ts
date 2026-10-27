/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObjects} from 'bpmn-js/lib/NavigatedViewer';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {hasCalledProcessInstances} from './elements';

describe('hasCalledProcessInstances', () => {
	it('should detect call activities', () => {
		const businessObjects: BusinessObjects = {
			userTask: {id: 'userTask', name: 'User task', $type: 'bpmn:UserTask'},
			callActivity: {id: 'callActivity', name: 'Called process', $type: 'bpmn:CallActivity'},
		};

		expect(hasCalledProcessInstances(businessObjects)).toBe(true);
	});

	it('should return false without call activities', () => {
		const businessObjects: BusinessObjects = {
			userTask: {id: 'userTask', name: 'User task', $type: 'bpmn:UserTask'},
		};

		expect(hasCalledProcessInstances(businessObjects)).toBe(false);
		expect(hasCalledProcessInstances()).toBe(false);
	});
});
