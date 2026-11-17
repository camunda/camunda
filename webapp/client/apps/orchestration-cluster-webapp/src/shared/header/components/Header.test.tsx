/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanup} from 'vitest-browser-react';
import {afterEach, beforeEach, describe, expect} from 'vitest';
import {storeSessionState} from '#/shared/browser-storage/session-storage';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {it} from '#/vitest-modules/test-extend';
import {createCurrentUser} from '#/shared-test-modules/api-mocks/current-user';
import {createSystemConfiguration} from '#/shared-test-modules/api-mocks/system-configuration';
import {Header} from './Header';

describe('<Header /> (carbon)', () => {
	beforeEach(() => {
		storeSessionState('clientConfig', createSystemConfiguration());
	});

	afterEach(async () => {
		await cleanup();
		sessionStorage.clear();
	});

	it('should hide the license tag and keep rendering when the license is unavailable', async () => {
		// given
		// the license request failed, so no license is available to the header

		// when
		const screen = await renderWithRouter(
			() => <Header currentUser={createCurrentUser()} license={null} />,
			{path: '/operate'},
		);

		// then
		await expect.element(screen.getByRole('banner')).toBeVisible();
		await expect.element(screen.getByText('Non-production license', {exact: true})).not.toBeInTheDocument();
		await expect.element(screen.getByText('Non-commercial license')).not.toBeInTheDocument();
	});
});
