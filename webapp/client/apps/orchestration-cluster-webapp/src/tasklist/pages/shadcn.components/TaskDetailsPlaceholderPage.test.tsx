/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {expect} from 'vitest';
import {TaskDetailsPlaceholderPage} from './TaskDetailsPlaceholderPage';

it('should display the task details placeholder', async () => {
	const screen = await render(<TaskDetailsPlaceholderPage userTaskKey="2251799813685281" />);

	await expect.element(screen.getByRole('heading', {name: 'Details'})).toBeVisible();
	await expect.element(screen.getByText('2251799813685281')).toBeVisible();
});
