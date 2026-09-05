/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';
import {NoTasks} from './NoTasks';

describe('<NoTasks />', () => {
	it('should render the empty state heading and description', async () => {
		const screen = await render(<NoTasks />);

		await expect.element(screen.getByText('No tasks found')).toBeVisible();
		await expect.element(screen.getByText('There are no tasks matching your filter criteria.')).toBeVisible();
	});
});
