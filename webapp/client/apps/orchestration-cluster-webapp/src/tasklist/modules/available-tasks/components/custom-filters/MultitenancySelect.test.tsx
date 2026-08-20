/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {mockCurrentUserEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {MultitenancySelect} from './MultitenancySelect';
import {renderWithRouter} from '#/vitest-modules/render-with-router';

describe('<MultitenancySelect />', () => {
	it('should render tenant options from the current user', async ({worker}) => {
		worker.use(
			mockCurrentUserEndpoint({
				successResponse: HttpResponse.json(
					createCurrentUser({
						tenants: [
							{tenantId: '<default>', name: 'Default', description: null},
							{tenantId: 'tenant-a', name: 'Tenant A', description: null},
						],
					}),
				),
			}),
		);

		const screen = await renderWithRouter(() => <MultitenancySelect id="tenant" name="tenant" labelText="Tenant" />, {
			path: '/tasklist',
		});

		const combobox = screen.getByRole('combobox', {name: /tenant/i});
		await expect.element(combobox).toBeVisible();

		await userEvent.selectOptions(combobox, '<default>');
		await expect.element(combobox).toHaveValue('<default>');

		await userEvent.selectOptions(combobox, 'tenant-a');
		await expect.element(combobox).toHaveValue('tenant-a');
	});
});
