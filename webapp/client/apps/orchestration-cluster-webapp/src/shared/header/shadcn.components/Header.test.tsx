/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {cleanup} from 'vitest-browser-react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {authenticationStore} from '#/shared/auth/authentication.store';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {it} from '#/vitest-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {mockCurrentUserEndpoint, mockLicenseEndpoint} from '#/shared-test-modules/mock-handlers';
import {Header} from './Header';

describe('<Header /> (V2)', () => {
	beforeEach(() => {
		storeSessionState('clientConfig', createSystemConfiguration());
	});

	afterEach(async () => {
		await cleanup();
		authenticationStore.reset();
		sessionStorage.clear();
	});

	it('should render a header', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockLicenseEndpoint({
				successResponse: HttpResponse.json(createLicense({validLicense: false, isCommercial: false, expiresAt: null})),
			}),
		);

		const screen = await renderWithRouter(
			() => (
				<Header>
					<div>Page content</div>
				</Header>
			),
			{path: '/shadcn/tasklist'},
		);

		await expect.element(screen.getByRole('banner')).toBeVisible();
		await expect.element(screen.getByText('Non-Production License')).toBeVisible();
		await expect.element(screen.getByText('Non-commercial license')).toBeVisible();
		await expect.element(screen.getByRole('navigation', {name: 'Camunda context'})).toBeVisible();
		await expect.element(screen.getByRole('link', {name: 'Tasklist'})).toHaveAttribute('href', '/shadcn/tasklist');
		await expect.element(screen.getByRole('button', {name: 'Info'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Settings'})).toBeVisible();
		await expect.element(screen.getByText('Page content')).toBeVisible();
	});

	it('should hide nav links if application is unauthorized', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: []})),
			}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		);

		const screen = await renderWithRouter(() => <Header>Page content</Header>, {path: '/shadcn/tasklist'});

		await expect.element(screen.getByRole('link', {name: 'Tasks'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'Processes'})).not.toBeInTheDocument();
	});

	it.for([
		{path: '/shadcn/tasklist/$userTaskKey' as const, initialEntry: '/shadcn/tasklist/task-42'},
		{path: '/shadcn/tasklist/$userTaskKey/process' as const, initialEntry: '/shadcn/tasklist/task-42/process'},
		{path: '/shadcn/tasklist/$userTaskKey/history' as const, initialEntry: '/shadcn/tasklist/task-42/history'},
	])('should mark Tasks as active at $initialEntry', async ({path, initialEntry}, {worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		);

		const screen = await renderWithRouter(() => <Header>Page content</Header>, {path, initialEntry});

		await expect.element(screen.getByRole('link', {name: 'Tasks'})).toHaveAttribute('aria-current', 'page');
		await expect.element(screen.getByRole('link', {name: 'Processes'})).not.toHaveAttribute('aria-current');
	});

	it('should mark Processes as active on the processes route', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({successResponse: HttpResponse.json(createCurrentUser())}),
			mockLicenseEndpoint({successResponse: HttpResponse.json(createLicense())}),
		);

		const screen = await renderWithRouter(() => <Header>Page content</Header>, {
			path: '/shadcn/tasklist/processes',
		});

		await expect.element(screen.getByRole('link', {name: 'Processes'})).toHaveAttribute('aria-current', 'page');
		await expect.element(screen.getByRole('link', {name: 'Tasks'})).not.toHaveAttribute('aria-current');
	});
});
