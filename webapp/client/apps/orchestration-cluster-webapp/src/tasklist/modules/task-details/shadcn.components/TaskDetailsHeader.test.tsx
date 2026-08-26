/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {TaskDetailsHeader} from './TaskDetailsHeader';

describe('<TaskDetailsHeader />', () => {
	it('should render task name and process name', async () => {
		const screen = await render(<TaskDetailsHeader taskName="Review invoice" processName="Invoice process" />);

		await expect.element(screen.getByTitle('Task details header')).toBeVisible();
		await expect.element(screen.getByText('Review invoice')).toBeVisible();
		await expect.element(screen.getByText('Invoice process')).toBeVisible();
	});
});
