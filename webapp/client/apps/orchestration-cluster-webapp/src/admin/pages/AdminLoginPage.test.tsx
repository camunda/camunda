/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {AdminLoginPage} from './AdminLoginPage';

describe('<AdminLoginPage />', () => {
	it('should identify itself as the Admin login', async () => {
		const screen = await renderWithRouter(AdminLoginPage, {path: '/admin/login'});

		await expect.element(screen.getByRole('heading', {name: 'Admin'})).toBeVisible();
	});
});
