/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {describe, expect} from 'vitest';
import {MetricPanel} from './MetricPanel';

describe('<MetricPanel />', () => {
	it('should render the InstancesBar with the running instance counts', async () => {
		const screen = await renderWithRouter(
			() => <MetricPanel count={{total: 10, withIncidents: 4, withoutIncidents: 6}} />,
			{path: '/operate-preview'},
		);

		await expect.element(screen.getByTestId('active-instances-badge')).toHaveTextContent('6');
		await expect.element(screen.getByTestId('incident-instances-badge')).toHaveTextContent('4');
	});
});
