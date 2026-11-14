/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {QueryClient} from '@tanstack/react-query';
import isEqual from 'lodash/isEqual';
import type {QueryVariablesResponseBody, Variable} from '@camunda/camunda-api-zod-schemas/8.11';
import {endpoints} from '#/shared/http/endpoints';
import {request} from '#/shared/http/request';
import {waitUntilReady} from '#/shared/http/waitUntilReady';
import {instanceRequest} from './processInstance.queries';
import {variableQuery} from './variables.queries';

type VariableToSave = {
	processInstanceKey: string;
	scopeKey: string;
	name: string;
	value: string;
	variableKey?: string;
};

const isSameJson = (a: string, b: string) => {
	try {
		return isEqual(JSON.parse(a), JSON.parse(b));
	} catch {
		return a === b;
	}
};

async function waitForVariableValue(queryClient: QueryClient, variableKey: string, value: string) {
	const variable = await waitUntilReady(
		queryClient,
		{
			queryKey: ['savedVariable', variableKey],
			queryFn: ({signal}) => instanceRequest<Variable>(endpoints.getVariable({variableKey}), signal),
		},
		{isReady: (savedVariable) => isSameJson(savedVariable.value, value), retry: Infinity},
	);

	if (variable === undefined) {
		throw new Error('Variable not updated yet');
	}

	queryClient.setQueryData(variableQuery(variableKey).queryKey, variable);
}

async function saveVariable(
	queryClient: QueryClient,
	{processInstanceKey, scopeKey, name, value, variableKey}: VariableToSave,
) {
	const {error} = await request(
		endpoints.updateElementInstanceVariables(scopeKey, {variables: {[name]: JSON.parse(value)}, local: true}),
	);

	if (error !== null) {
		throw error;
	}

	if (variableKey !== undefined) {
		await waitForVariableValue(queryClient, variableKey, value);
		return;
	}

	const result = await waitUntilReady(
		queryClient,
		{
			queryKey: ['savedVariable', processInstanceKey, scopeKey, name],
			queryFn: ({signal}) =>
				instanceRequest<QueryVariablesResponseBody>(
					endpoints.queryVariables({filter: {name, processInstanceKey, scopeKey}}),
					signal,
				),
		},
		{
			isReady: ({items}) =>
				items.some((item) => item.name === name && (item.isTruncated || isSameJson(item.value, value))),
			retry: Infinity,
		},
	);
	const savedVariable = result?.items.find((item) => item.name === name);

	if (savedVariable === undefined) {
		throw new Error('Variable not found');
	}

	if (savedVariable.isTruncated) {
		await waitForVariableValue(queryClient, savedVariable.variableKey, value);
	}
}

export {saveVariable};
