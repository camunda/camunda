/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {afterEach, describe, expect, vi} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {createLicense} from '#/shared-test-modules/api-mocks/license';
import {LicenseBadges} from './LicenseBadges';

describe('license note', () => {
	afterEach(async () => {
		vi.useRealTimers();
	});

	it('should show license note in CCSM free/trial environment', async () => {
		const screen = await render(
			<LicenseBadges license={createLicense({validLicense: false, isCommercial: false, expiresAt: null})} />,
		);

		await expect.element(screen.getByText('Non-Production License')).toBeVisible();
		await expect
			.element(screen.getByText('Non-Production License'))
			.toHaveAttribute(
				'title',
				'Non-production license. For production usage details, visit our terms & conditions page or contact our sales team.',
			);
	});

	it('should not show license note in SaaS environment', async () => {
		const screen = await render(
			<LicenseBadges
				license={createLicense({licenseType: 'saas', validLicense: false, isCommercial: false, expiresAt: null})}
			/>,
		);

		expect(screen.getByText('Non-Production License').elements()).toHaveLength(0);
		expect(screen.getByText('Non-commercial license').elements()).toHaveLength(0);
	});

	it('should show license note in unknown environment', async () => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2026-01-02T00:00:00.000Z'));
		const screen = await render(
			<LicenseBadges
				license={createLicense({
					licenseType: 'unknown',
					validLicense: false,
					isCommercial: false,
					expiresAt: '2026-01-01T00:00:00.000Z',
				})}
			/>,
		);

		await expect.element(screen.getByText('Non-Production License')).toBeVisible();
		await expect.element(screen.getByText('Non-commercial license - expired')).toBeVisible();
	});

	it('should show production license note in CCSM enterprise environment', async () => {
		const screen = await render(<LicenseBadges license={createLicense()} />);

		await expect.element(screen.getByText('Production license')).toBeVisible();
		expect(screen.getByText('Non-Production License').elements()).toHaveLength(0);
	});

	it('should show non-commercial license note in self-managed enterprise environment', async () => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2026-01-02T00:00:00.000Z'));
		const screen = await render(
			<LicenseBadges license={createLicense({isCommercial: false, expiresAt: '2026-01-01T00:00:00.000Z'})} />,
		);

		await expect.element(screen.getByText('Non-commercial license - expired')).toBeVisible();
	});

	it('should hide commercial license note in self-managed if license is commercial', async () => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2026-01-02T00:00:00.000Z'));
		const screen = await render(
			<LicenseBadges license={createLicense({isCommercial: true, expiresAt: '2026-01-01T00:00:00.000Z'})} />,
		);

		await expect.element(screen.getByText('Production license')).toBeVisible();
		expect(screen.getByText('Non-commercial license - expired').elements()).toHaveLength(0);
	});

	it('should show non-commercial license expiry date', async () => {
		vi.useFakeTimers();
		vi.setSystemTime(new Date('2026-01-01T00:00:00.000Z'));
		const screen = await render(
			<LicenseBadges license={createLicense({isCommercial: false, expiresAt: '2026-01-01T12:00:00.000Z'})} />,
		);

		await expect.element(screen.getByText('Non-commercial license - 0 days left')).toBeVisible();
	});
});
