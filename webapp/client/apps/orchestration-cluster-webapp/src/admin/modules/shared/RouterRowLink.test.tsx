/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {cleanup} from 'vitest-browser-react';
import {userEvent} from 'vitest/browser';
import {afterEach, describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {RouterRowLink} from './RouterRowLink';
import {useAdminDetailPath} from './useAdminDetailPath';

const RowLink: React.FC = () => {
	const detailPath = useAdminDetailPath();
	return <RouterRowLink href={detailPath.user('ops/1')}>ops/1</RouterRowLink>;
};

describe('<RouterRowLink />', () => {
	afterEach(async () => {
		await cleanup();
	});

	it('should render the detail URL without a base path on root deployments', async () => {
		// given
		const screen = await renderWithRouter(RowLink, {path: '/admin/users'});

		// then
		await expect.element(screen.getByRole('link', {name: 'ops/1'})).toHaveAttribute('href', '/admin/users/ops%2F1');
	});

	it('should prefix the detail URL with the base path exactly once', async () => {
		// given
		const screen = await renderWithRouter(RowLink, {path: '/admin/users', basepath: '/base'});

		// then
		await expect
			.element(screen.getByRole('link', {name: 'ops/1'}))
			.toHaveAttribute('href', '/base/admin/users/ops%2F1');
	});

	it('should navigate client-side to the detail URL including the base path', async () => {
		// given
		const screen = await renderWithRouter(RowLink, {path: '/admin/users', basepath: '/base'});

		// when
		await userEvent.click(screen.getByRole('link', {name: 'ops/1'}));

		// then
		await expect.poll(() => screen.router.state.location.publicHref).toBe('/base/admin/users/ops%2F1');
	});
});
