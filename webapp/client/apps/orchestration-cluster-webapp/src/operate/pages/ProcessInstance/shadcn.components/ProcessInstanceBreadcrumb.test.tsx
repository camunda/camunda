/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {createCallHierarchy} from '#/shared-test-modules/api-mocks/call-hierarchy';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {ProcessInstanceBreadcrumb} from './ProcessInstanceBreadcrumb';

const instance = createProcessInstance({processInstanceKey: '99', processDefinitionName: 'Current Process'});

function renderBreadcrumb(names: string[]) {
	const callHierarchy = names.map((name, index) =>
		createCallHierarchy({processInstanceKey: String(index + 1), processDefinitionName: name}),
	);
	return renderWithRouter(
		() => <ProcessInstanceBreadcrumb callHierarchy={callHierarchy} processInstance={instance} />,
		{
			path: '/operate-preview/processes/$processInstanceId',
			initialEntry: '/operate-preview/processes/99',
		},
	);
}

describe('<ProcessInstanceBreadcrumb /> (DS)', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should link the ancestors and mark the current instance', async () => {
		const screen = await renderBreadcrumb(['Root', 'Parent']);

		await expect
			.element(screen.getByRole('link', {name: 'View Process Root - Instance 1'}))
			.toHaveAttribute('href', '/operate-preview/processes/1');
		await expect
			.element(screen.getByRole('link', {name: 'View Process Parent - Instance 2'}))
			.toHaveAttribute('href', '/operate-preview/processes/2');
		await expect.element(screen.getByText('Current Process')).toHaveAttribute('aria-current', 'page');
	});

	it('should collapse the middle ancestors into a navigable menu', async () => {
		const screen = await renderBreadcrumb(['A', 'B', 'C', 'D', 'E']);

		await expect.element(screen.getByRole('link', {name: 'View Process A - Instance 1'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View Process E - Instance 5'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'View Process C - Instance 3'})).not.toBeInTheDocument();

		await userEvent.click(screen.getByRole('button', {name: 'More'}));
		await userEvent.click(screen.getByRole('menuitem', {name: 'C'}));

		await expect.poll(() => screen.router.state.location.pathname).toBe('/operate-preview/processes/3');
	});
});
