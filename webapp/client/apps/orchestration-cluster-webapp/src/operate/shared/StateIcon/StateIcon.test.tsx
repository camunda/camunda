/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {StateIcon} from './StateIcon';

describe('<StateIcon />', () => {
	it.for([
		{state: 'ACTIVE', name: 'active state'},
		{state: 'INCIDENT', name: 'incident state'},
		{state: 'COMPLETED', name: 'completed state'},
		{state: 'TERMINATED', name: 'terminated state'},
		{state: 'SUSPENDED', name: 'suspended state'},
		{state: 'EVALUATED', name: 'evaluated state'},
		{state: 'FAILED', name: 'failed state'},
		{state: 'UNSPECIFIED', name: 'unspecified state'},
		{state: 'UNKNOWN', name: 'unknown state'},
	] as const)('should expose $state through its accessible name', async ({state, name}) => {
		const screen = await render(<StateIcon state={state} size={24} />);

		await expect.element(screen.getByRole('img', {name, exact: true})).toBeVisible();
	});
});
