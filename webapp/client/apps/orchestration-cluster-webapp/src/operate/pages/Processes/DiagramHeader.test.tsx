/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {DiagramHeader} from './DiagramHeader';

describe('<DiagramHeader />', () => {
	it('shows a generic title when there is no matching definition', async () => {
		const screen = await render(<DiagramHeader processDefinitionSelection={{kind: 'no-match'}} />);

		await expect.element(screen.getByText('Process')).toBeVisible();
	});

	it('shows the process name and ID for a single selected version', async () => {
		const screen = await render(
			<DiagramHeader
				processDefinitionSelection={{
					kind: 'single-version',
					definition: createProcessDefinition({name: 'Order Process', processDefinitionKey: '123'}),
				}}
			/>,
		);

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByText('my-process:1:0')).toBeVisible();
	});

	it('shows the version tag when present', async () => {
		const screen = await render(
			<DiagramHeader
				processDefinitionSelection={{
					kind: 'single-version',
					definition: createProcessDefinition({versionTag: 'release-1'}),
				}}
			/>,
		);

		await expect.element(screen.getByText('release-1')).toBeVisible();
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

		await expect.element(screen.getByText('Order Process')).toBeVisible();
		await expect.element(screen.getByText('order-process')).toBeVisible();
	});
});
