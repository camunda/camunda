/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {QueryClient} from '@tanstack/react-query';
import {describe, expect} from 'vitest';
import {http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {createQueryVariablesByUserTaskResponse, createVariable} from '#/shared-test-modules/api-mocks/variables';
import {queries} from './queries';

const redacted = createVariable({
	variableKey: '1',
	name: 'password',
	value: 'null',
	metadata: {protectionModes: ['REDACT']},
});
const plain = createVariable({variableKey: '2', name: 'amount', value: '100'});

describe('variable queries', () => {
	it('should redact protected values when searching the variables of a user task', async ({worker}) => {
		// given
		worker.use(
			http.post(endpoints.queryVariablesByUserTask.getUrl({userTaskKey: ':userTaskKey'}), () =>
				HttpResponse.json(createQueryVariablesByUserTaskResponse({items: [redacted, plain]})),
			),
		);

		// when
		const result = await new QueryClient().fetchQuery(queries.queryVariablesByUserTask('123', {}));

		// then
		expect(result.items.map(({name, value}) => ({name, value}))).toEqual([
			{name: 'password', value: '"[REDACTED]"'},
			{name: 'amount', value: '100'},
		]);
	});

	it('should redact protected values when loading all the variables of a user task', async ({worker}) => {
		// given
		worker.use(
			http.post(endpoints.queryVariablesByUserTask.getUrl({userTaskKey: ':userTaskKey'}), () =>
				HttpResponse.json(createQueryVariablesByUserTaskResponse({items: [redacted, plain]})),
			),
		);

		// when
		const result = await new QueryClient().fetchInfiniteQuery(queries.queryAllVariablesByUserTask('123'));

		// then
		expect(result.pages[0]?.items.map(({name, value}) => ({name, value}))).toEqual([
			{name: 'password', value: '"[REDACTED]"'},
			{name: 'amount', value: '100'},
		]);
	});

	it('should redact the value of a protected variable when fetching it by key', async ({worker}) => {
		// given
		worker.use(
			http.get(endpoints.getVariable.getUrl({variableKey: ':variableKey'}), () => HttpResponse.json(redacted)),
		);

		// when
		const result = await new QueryClient().fetchQuery(queries.getVariable('1'));

		// then
		expect(result.value).toBe('"[REDACTED]"');
	});
});
