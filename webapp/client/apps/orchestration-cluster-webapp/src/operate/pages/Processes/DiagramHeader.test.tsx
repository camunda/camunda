/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {delay, http, HttpResponse} from 'msw';
import {endpoints} from '@camunda/camunda-api-zod-schemas/8.11';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockQueryProcessDefinitionsEndpoint,
	mockQueryProcessInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {
	createProcessDefinition,
	createQueryProcessDefinitionsResponse,
} from '#/shared-test-modules/api-mocks/process-definitions';
import {createQueryProcessInstancesResponse} from '#/shared-test-modules/api-mocks/process-instances';
import {DiagramHeader} from './DiagramHeader';

function renderSingleVersion(definition: ReturnType<typeof createProcessDefinition>) {
	return renderWithRouter(() => <DiagramHeader processDefinitionSelection={{kind: 'single-version', definition}} />, {
		path: '/operate/processes',
		initialEntry: '/operate/processes',
	});
}

describe('<DiagramHeader />', () => {
	it('shows a generic title when there is no matching definition', async () => {
		const screen = await render(<DiagramHeader processDefinitionSelection={{kind: 'no-match'}} />);

		await expect.element(screen.getByText('Process')).toBeVisible();
	});

	it('should show the process name, ID and deletion for a single selected version', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		);
		const screen = await renderSingleVersion(
			createProcessDefinition({name: 'Order Process', processDefinitionKey: '123'}),
		);

		await expect.element(screen.getByRole('heading', {name: 'Order Process'})).toBeVisible();
		await expect.element(screen.getByTitle('my-process:1:0')).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: 'Delete Process Definition "Order Process - Version 1"'}))
			.toBeEnabled();
	});

	it('shows the version tag when present', async ({worker}) => {
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			mockQueryProcessInstancesEndpoint({successResponse: HttpResponse.json(createQueryProcessInstancesResponse())}),
		);
		const screen = await renderSingleVersion(createProcessDefinition({versionTag: 'release-1'}));

		await expect.element(screen.getByText('release-1')).toBeVisible();
		await expect.poll(() => screen.queryClient.isFetching()).toBe(0);
	});

	it('does not show a version tag for an all-versions selection', async () => {
		const screen = await render(
			<DiagramHeader
				processDefinitionSelection={{
					kind: 'all-versions',
					definition: {name: 'Order Process', processDefinitionId: 'order-process'},
				}}
			/>,
		);

		await expect.element(screen.getByRole('heading', {name: 'Order Process'})).toBeVisible();
		await expect.element(screen.getByTitle('order-process')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: /^Delete Process Definition/})).not.toBeInTheDocument();
	});

	it("should not show the previous version's running instances after switching versions", async ({worker}) => {
		const version1 = createProcessDefinition({name: 'Order Process', processDefinitionKey: 'key-1', version: 1});
		const version2 = createProcessDefinition({name: 'Order Process', processDefinitionKey: 'key-2', version: 2});
		worker.use(
			mockQueryProcessDefinitionsEndpoint({
				successResponse: HttpResponse.json(createQueryProcessDefinitionsResponse()),
			}),
			http.post(endpoints.queryProcessInstances.getUrl(), async ({request}) => {
				const {filter} = (await request.json()) as {filter: {processDefinitionKey: {$eq: string}}};
				if (filter.processDefinitionKey.$eq === 'key-2') {
					await delay('infinite');
				}
				return HttpResponse.json(createQueryProcessInstancesResponse({page: {totalItems: 5}}));
			}),
		);
		let showVersion2 = () => {};
		const Header = () => {
			const [definition, setDefinition] = useState(version1);
			showVersion2 = () => setDefinition(version2);
			return <DiagramHeader processDefinitionSelection={{kind: 'single-version', definition}} />;
		};
		const screen = await renderWithRouter(Header, {path: '/operate/processes', initialEntry: '/operate/processes'});
		await expect
			.element(screen.getByRole('button', {name: 'Only process definitions without running instances can be deleted.'}))
			.toBeDisabled();

		showVersion2();

		await expect
			.element(screen.getByRole('button', {name: 'Delete Process Definition "Order Process - Version 2"'}))
			.toBeEnabled();
	});
});
