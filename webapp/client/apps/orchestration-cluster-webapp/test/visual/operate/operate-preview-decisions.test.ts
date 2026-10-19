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
	mockLicenseEndpoint,
	mockSystemConfigurationEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';

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
	);
});

test('should match the resized decisions panels snapshot after dragging the resize handle', async ({
	operatePreviewDecisionsPage,
	page,
}) => {
	await operatePreviewDecisionsPage.goto();
	await expect(operatePreviewDecisionsPage.instancesTablePlaceholder).toBeVisible();

	await operatePreviewDecisionsPage.panelResizeHandle.dragTo(operatePreviewDecisionsPage.decisionPanelPlaceholder, {
		force: true,
	});

	await expect(page.locator('body')).not.toHaveClass(/cursor-ns-resize/);
	await expect
		.poll(() =>
			page.evaluate(() => {
				const panelStates = JSON.parse(localStorage.getItem('operate.panelStates') ?? '{}');
				return panelStates['decisions-instances-vertical-panel']?.[0];
			}),
		)
		.toBeLessThan(50);
	await expect(page).toHaveScreenshot();
});

test('should match the filters panel snapshot with populated optional filters', async ({page}) => {
	await page.goto(
		'/operate-preview/decisions?evaluated=true&failed=true&businessId=eq_order-1&processInstanceKey=2251799813685249&evaluationDateFrom=2024-01-01T00:00:00.000Z&evaluationDateTo=2024-01-02T00:00:00.000Z',
	);
	const filtersPanel = page.getByRole('region', {name: 'Filter', exact: true});
	await expect(page.getByLabel('Business ID', {exact: true})).toHaveValue('order-1');

	await expect(filtersPanel).toHaveScreenshot();
});

test('should match the date range dialog snapshot', async ({operatePreviewDecisionsPage, page}) => {
	// The calendar opens on the current month and highlights today
	await page.clock.setFixedTime(new Date('2026-10-20T12:00:00Z'));
	await operatePreviewDecisionsPage.goto();
	await operatePreviewDecisionsPage.moreFiltersButton.click();
	await page.getByRole('menuitem', {name: 'Evaluation Date Range'}).click();
	const dialog = page.getByTestId('date-range-modal');
	await expect(dialog).toBeVisible();

	await expect(dialog).toHaveScreenshot();
});
