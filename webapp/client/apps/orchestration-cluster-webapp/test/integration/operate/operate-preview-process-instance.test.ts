/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {HttpResponse} from 'msw';
import {
	mockCurrentUserEndpoint,
	mockGetProcessInstanceEndpoint,
	mockGetProcessInstanceWaitStateStatisticsEndpoint,
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';

const PROCESS_INSTANCE_ID = '2251799813685280';

test.beforeEach(({network}) => {
	network.use(
		mockCurrentUserEndpoint({
			successResponse: HttpResponse.json(createCurrentUser({authorizedComponents: ['operate']})),
		}),
		mockSystemConfigurationEndpoint({
			successResponse: HttpResponse.json(createSystemConfiguration({components: {active: ['operate']}})),
		}),
		mockLicenseEndpoint({
			successResponse: HttpResponse.json(createLicense()),
		}),
		mockGetProcessInstanceEndpoint({
			successResponse: HttpResponse.json(createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})),
		}),
		mockGetProcessInstanceWaitStateStatisticsEndpoint({
			successResponse: HttpResponse.json(createPaginatedResponse({items: []})),
		}),
	);
});

test.describe('Operate Process Instance DS preview (/operate-preview/processes/:processInstanceId)', () => {
	test('should render the placeholder shell', async ({operatePreviewProcessInstancePage}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(operatePreviewProcessInstancePage.heading).toBeAttached();
		await expect(operatePreviewProcessInstancePage.headerPlaceholder).toBeVisible();
		await expect(operatePreviewProcessInstancePage.diagramPanelPlaceholder).toBeVisible();
		await expect(operatePreviewProcessInstancePage.bottomPanelPlaceholder).toBeVisible();
		await expect(operatePreviewProcessInstancePage.variablesTab).toBeVisible();
	});

	test('should redirect to the default tab and switch tabs through the URL', async ({
		operatePreviewProcessInstancePage,
		page,
	}) => {
		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/variables`);

		await operatePreviewProcessInstancePage.incidentsTab.click();

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/incidents`);
		await expect(operatePreviewProcessInstancePage.incidentsTab).toHaveAttribute('aria-selected', 'true');
	});

	test('should redirect to the details tab when the instance waits at process level', async ({
		network,
		operatePreviewProcessInstancePage,
		page,
	}) => {
		network.use(
			mockGetProcessInstanceWaitStateStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createPaginatedResponse({items: [{elementId: 'my-process', waitingCount: 1}]}),
				),
			}),
		);

		await operatePreviewProcessInstancePage.goto(PROCESS_INSTANCE_ID);

		await expect(page).toHaveURL(`/operate-preview/processes/${PROCESS_INSTANCE_ID}/details`);
	});
});
