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

// this negative test must be on a separate test file because of Vitest's cache

describe('<Disclaimer /> enterprise', () => {
	it('should not render the disclaimer in enterprise mode', async () => {
		document.documentElement.dataset['isEnterprise'] = 'true';
		const screen = await render(<Disclaimer />);

		await expect.element(screen.getByRole('link', {name: 'terms & conditions page'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('link', {name: 'contact sales'})).not.toBeInTheDocument();

		delete document.documentElement.dataset['isEnterprise'];
	});
});
