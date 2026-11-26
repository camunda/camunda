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

function renderHeader(isDrdOpen = false) {
	return renderWithRouter(
		() => <Header decisionEvaluationInstanceKey={DECISION_INSTANCE_ID} onOpenDrd={() => {}} isDrdOpen={isDrdOpen} />,
		{
			path: '/operate/decisions/$decisionInstanceId',
			initialEntry: `/operate/decisions/${DECISION_INSTANCE_ID}`,
		},
	);
}

const CURRENT_USER_WITH_TENANTS = HttpResponse.json(
	createCurrentUser({
		tenants: [
			{tenantId: '<default>', name: 'Default Tenant', description: null},
			{tenantId: 'tenant-a', name: 'Tenant A', description: null},
		],
	}),
);

const enableMultiTenancy = () =>
	sessionStorage.setItem(
		'clientConfig',
		JSON.stringify(
			createSystemConfiguration({
				deployment: {
					isMultiTenancyEnabled: true,
					isTenantsApiEnabled: true,
					isWaitStatesEnabled: true,
					maxRequestSize: 0,
				},
			}),
		),
	);

describe('<Header /> (design system) - process instance and DRD button', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should show no process instance link when the process instance key is -1', async ({worker}) => {
		// given
		enableMultiTenancy();
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({processInstanceKey: '-1'})),
			}),
		);

		// when
		const screen = await renderHeader();

		// then
		await expect.element(screen.getByText('None')).toBeVisible();
		await expect.element(screen.getByRole('link', {name: /View process instance/})).not.toBeInTheDocument();
	});

	it('should hide the open DRD button while the DRD panel is open', async ({worker}) => {
		// given
		enableMultiTenancy();
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({successResponse: HttpResponse.json(createDecisionInstance())}),
		);

		// when
		const screen = await renderHeader(true);

		// then
		await expect.element(screen.getByText('Invoice Classification')).toBeVisible();
		await expect
			.element(screen.getByRole('button', {name: 'Open decision requirements diagram'}))
			.not.toBeInTheDocument();
	});
});

describe('<Header /> (design system) - Business ID and tenant', () => {
	afterEach(() => {
		sessionStorage.clear();
	});

	it('should render the Business ID when present', async ({worker}) => {
		// given
		enableMultiTenancy();
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({businessId: 'order-12345'})),
			}),
		);

		// when
		const screen = await renderHeader();

		// then
		await expect.element(screen.getByText('Business ID')).toBeVisible();
		await expect.element(screen.getByText('order-12345')).toBeVisible();
	});

	it('should not render a Business ID when it is null', async ({worker}) => {
		// given
		enableMultiTenancy();
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({businessId: null})),
			}),
		);

		// when
		const screen = await renderHeader();

		// then
		await expect.element(screen.getByText('Version')).toBeVisible();
		await expect.element(screen.getByText('Business ID')).not.toBeInTheDocument();
	});

	it('should render the tenant name when multi-tenancy is enabled', async ({worker}) => {
		// given
		enableMultiTenancy();
		worker.use(
			mockCurrentUserEndpoint({successResponse: CURRENT_USER_WITH_TENANTS}),
			mockGetDecisionInstanceEndpoint({
				successResponse: HttpResponse.json(createDecisionInstance({tenantId: 'tenant-a'})),
			}),
		);

		// when
		const screen = await renderHeader();

		// then
		await expect.element(screen.getByText('Tenant')).toBeVisible();
		await expect.element(screen.getByText('Tenant A')).toBeVisible();
	});
});
