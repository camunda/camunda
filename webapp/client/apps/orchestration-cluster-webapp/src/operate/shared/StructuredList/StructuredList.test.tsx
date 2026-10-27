/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, it, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {StructuredList} from './StructuredList';

describe('<StructuredList />', () => {
	it('renders header and row columns', async () => {
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Process Definition'}]}
				rows={[{key: 'order-process-v2', columns: [{cellContent: 'Order Process - Version 2'}]}]}
			/>,
		);

		await expect.element(screen.getByText('Process Definition')).toBeVisible();
		await expect.element(screen.getByText('Order Process - Version 2')).toBeVisible();
	});

	it('renders dynamic rows alongside the static rows', async () => {
		const screen = await render(
			<StructuredList
				label="Process Details"
				headerColumns={[{cellContent: 'Name'}]}
				rows={[{key: 'static-row', columns: [{cellContent: 'Static'}]}]}
				dynamicRows={<div>Dynamic content</div>}
			/>,
		);

		await expect.element(screen.getByText('Dynamic content')).toBeVisible();
		await expect.element(screen.getByText('Static')).toBeVisible();
	});
});
