/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {createElementInstanceInspection} from '#/shared-test-modules/api-mocks/element-instance-inspection';
import {getWaitStateStatusItems} from './waitStates';

describe('getWaitStateStatusItems', () => {
	it('should describe each kind of wait state', () => {
		// given
		const waitStates = [
			createElementInstanceInspection({details: {waitStateType: 'MESSAGE', messageName: 'paid', correlationKey: null}}),
			createElementInstanceInspection({details: {waitStateType: 'SIGNAL', signalName: 'go'}}),
			createElementInstanceInspection({
				details: {waitStateType: 'CONDITION', expression: '=ready', events: ['update']},
			}),
			createElementInstanceInspection({details: {waitStateType: 'USER_TASK', taskKey: '1', dueDate: null}}),
		];

		// when
		const items = getWaitStateStatusItems(waitStates);

		// then
		expect(items.map(({text}) => text)).toEqual([
			'Waiting for message: paid',
			'Waiting for signal: go',
			'Waiting for condition',
			'Waiting for task completion',
		]);
	});

	it('should describe job wait states by job kind', () => {
		// given
		const job = (jobKind: 'BPMN_ELEMENT' | 'EXECUTION_LISTENER' | 'TASK_LISTENER') =>
			createElementInstanceInspection({
				details: {waitStateType: 'JOB', jobType: 'notify', jobKind, jobKey: '1', listenerEventType: null, retries: 3},
			});

		// when
		const items = getWaitStateStatusItems([job('BPMN_ELEMENT'), job('EXECUTION_LISTENER'), job('TASK_LISTENER')]);

		// then
		expect(items.map(({text}) => text)).toEqual([
			'Waiting for job: notify',
			'Waiting for execution listener: notify',
			'Waiting for task listener: notify',
		]);
	});

	it('should merge several timers into one status', () => {
		// given
		const timer = (dueDate: number | null) =>
			createElementInstanceInspection({details: {waitStateType: 'TIMER', dueDate, repetitions: null}});

		// when
		const items = getWaitStateStatusItems([timer(2_000_000_000_000), timer(1_000_000_000_000), timer(null)]);

		// then
		expect(items).toHaveLength(1);
		expect(items[0]?.text).toContain('Waiting for timer: due date');
	});

	it('should describe a timer without a due date', () => {
		// given
		const waitStates = [
			createElementInstanceInspection({details: {waitStateType: 'TIMER', dueDate: null, repetitions: null}}),
		];

		// when
		const items = getWaitStateStatusItems(waitStates);

		// then
		expect(items.map(({text}) => text)).toEqual(['Waiting for timer']);
	});
});
