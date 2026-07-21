/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {mockCurrentUserEndpoint, mockGetDecisionInstanceEndpoint} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createDecisionInstance} from '#/shared-test-modules/api-mocks/decision-instances';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Header} from './Header';

const DECISION_INSTANCE_ID = '123567';

function renderHeader() {
	return renderWithRouter(() => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} />, {
		path: '/operate/decisions/$decisionInstanceId',
		initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
	});
}

const CURRENT_USER_WITH_TENANTS = HttpResponse.json(
	createCurrentUser({
		tenants: [
			{tenantId: '<default>', name: 'Default Tenant', description: null},
			{tenantId: 'tenant-a', name: 'Tenant A', description: null},
		],
	}),
);

describe('<Header /> - multi tenancy', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render multi tenancy column and include tenant in version link', async ({worker}) => {
		sessionStorage.setItem(
			'clientConfig',
			JSON.stringify(createSystemConfiguration({deployment: {isMultiTenancyEnabled: true, maxRequestSize: 0}})),
		);
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(createDecisionInstance())}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByText('Default Tenant')).toBeVisible();
		await expect
			.element(
				screen.getByRole('link', {
					name: 'View decision "Invoice Classification version 1" instances - Default Tenant',
				}),
			)
			.toBeVisible();
	});

	it('should hide multi tenancy column and exclude tenant from version link', async ({worker}) => {
		sessionStorage.setItem('clientConfig', JSON.stringify(createSystemConfiguration()));
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(createDecisionInstance())}),
		);

		const screen = await renderHeader();

		await expect.element(screen.getByText('Version')).toBeVisible();
		await expect.element(screen.getByText('Default Tenant')).not.toBeInTheDocument();
		await expect
			.element(screen.getByRole('link', {name: 'View decision "Invoice Classification version 1" instances'}))
			.toBeVisible();
	});
});
