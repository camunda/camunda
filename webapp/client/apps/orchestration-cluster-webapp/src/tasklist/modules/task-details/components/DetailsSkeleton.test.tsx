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
import {DetailsSkeleton} from './DetailsSkeleton';

describe('<DetailsSkeleton />', () => {
	it('should render with the provided data-testid', async () => {
		const screen = await render(<DetailsSkeleton data-testid="details-skeleton" />);

		await expect.element(screen.getByTestId('details-skeleton')).toBeVisible();
	});
});
