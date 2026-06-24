/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {Disclaimer} from './index';
import {it} from '#/vitest-modules/test-extend';
import {describe, expect} from 'vitest';

describe('<Disclaimer />', () => {
	it('should show the disclaimer', async () => {
		const screen = await render(<Disclaimer />);

		await expect
			.element(screen.getByRole('link', {name: 'terms & conditions page'}))
			.toHaveAttribute('href', 'https://legal.camunda.com/#self-managed-non-production-terms');
		await expect
			.element(screen.getByRole('link', {name: 'contact sales'}))
			.toHaveAttribute('href', 'https://camunda.com/contact/');
	});
});
