/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {Route} from './$decisionInstanceId';

describe('/_auth/operate/decisions/$decisionInstanceId head', () => {
	it('should set a dynamic page title once the decision instance name is known', async () => {
		const decisionInstance = createDecisionInstance({decisionDefinitionName: 'Invoice Classification'});

		const head = await Route.options.head?.({
			loaderData: decisionInstance,
			params: {decisionInstanceId: decisionInstance.decisionEvaluationInstanceKey},
			// eslint-disable-next-line @typescript-eslint/no-explicit-any
		} as any);

		expect(head?.meta).toEqual([
			{
				title: `Operate: Decision Instance ${decisionInstance.decisionEvaluationInstanceKey} of Invoice Classification`,
			},
		]);
	});

	it('should not override the parent title when the decision instance could not be loaded', async () => {
		const head = await Route.options.head?.({
			loaderData: undefined,
			params: {decisionInstanceId: '123'},
			// eslint-disable-next-line @typescript-eslint/no-explicit-any
		} as any);

		expect(head?.meta).toEqual([]);
	});
});
