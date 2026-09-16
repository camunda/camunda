/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from './render-with-router';

it.for([
	{basepath: '', expectedEntry: '/operate/processes'},
	{basepath: '/', expectedEntry: '/operate/processes'},
	{basepath: '/camunda', expectedEntry: '/camunda/operate/processes'},
	{basepath: '/camunda/', expectedEntry: '/camunda/operate/processes'},
	{
		basepath: '/camunda',
		initialEntry: '/camunda/operate/processes?active=true',
		expectedEntry: '/camunda/operate/processes?active=true',
	},
])('should render a route at $expectedEntry', async ({basepath, initialEntry, expectedEntry}) => {
	const screen = await renderWithRouter(() => <h1>Route content</h1>, {
		path: '/operate/processes',
		basepath,
		initialEntry,
	});

	await expect.element(screen.getByRole('heading', {name: 'Route content'})).toBeVisible();
	expect(screen.router.history.location.href).toBe(expectedEntry);
});
