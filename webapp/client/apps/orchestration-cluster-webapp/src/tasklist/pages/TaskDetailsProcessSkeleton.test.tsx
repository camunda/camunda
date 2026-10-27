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
import {TaskDetailsProcessSkeleton} from './TaskDetailsProcessSkeleton';

describe('<TaskDetailsProcessSkeleton />', () => {
	it('should show a loading state while the process diagram is being prepared', async () => {
		const screen = await render(<TaskDetailsProcessSkeleton />);

		await expect.element(screen.getByTestId('process-tab-content')).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).not.toBeInTheDocument();
	});
});
