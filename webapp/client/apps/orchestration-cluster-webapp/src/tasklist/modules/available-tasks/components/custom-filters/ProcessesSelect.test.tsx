/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {ErrorBoundary} from 'react-error-boundary';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {mockQueryProcessDefinitionsEndpoint} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {ProcessesSelect} from './ProcessesSelect';
import {ProcessesSelectErrorFallback} from './ProcessesSelectErrorFallback';
import {renderWithRouter} from '#/vitest-modules/render-with-router';

describe('<ProcessesSelect />', () => {
	it('should render all processes plus fetched options', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({name: 'Order Process', processDefinitionKey: 'key-1', version: 3}),
							createProcessDefinition({name: 'Payment Process', processDefinitionKey: 'key-2', version: 1}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <ProcessesSelect id="bpmnProcess" name="bpmnProcess" labelText="Process" />,
			{path: '/tasklist'},
		);

		const combobox = screen.getByRole('combobox', {name: /process/i});
		await expect.element(combobox).toBeVisible();

		await userEvent.selectOptions(combobox, 'all');
		await expect.element(combobox).toHaveValue('all');

		await userEvent.selectOptions(combobox, 'key-1');
		await expect.element(combobox).toHaveValue('key-1');

		await userEvent.selectOptions(combobox, 'key-2');
		await expect.element(combobox).toHaveValue('key-2');
	});

	it('should fall back to processDefinitionId when name is null', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(
					createQueryProcessDefinitionsResponse({
						items: [
							createProcessDefinition({
								name: null as unknown as string,
								processDefinitionId: 'my-process:1:0',
								processDefinitionKey: 'key-1',
								version: 1,
							}),
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(
			() => <ProcessesSelect id="bpmnProcess" name="bpmnProcess" labelText="Process" />,
			{path: '/tasklist'},
		);

		const combobox = screen.getByRole('combobox', {name: /process/i});
		await expect.element(combobox).toBeVisible();
		await expect.element(combobox).toHaveTextContent(/my-process:1:0 - v1/i);
	});

	it('should render an error notification when the query fails', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: new HttpResponse(null, {status: 500}),
			}),
		);

		const screen = await renderWithRouter(
			() => (
				<ErrorBoundary FallbackComponent={ProcessesSelectErrorFallback}>
					{' '}
					<ProcessesSelect id="bpmnProcess" name="bpmnProcess" labelText="Process" />
				</ErrorBoundary>
			),
			{path: '/tasklist'},
		);

		await expect.element(screen.getByRole('alert')).toBeVisible();
	});
});
