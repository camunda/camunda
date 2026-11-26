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
import {InstancesList} from './InstancesList';

const SizedContainer: React.FC<{children?: React.ReactNode}> = ({children}) => (
	<div style={{height: '600px'}}>{children}</div>
);

describe('<InstancesList />', () => {
	it('should render the top and bottom panels', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top panel</div>} bottomPanel={<div>Bottom panel</div>} />,
			{wrapper: SizedContainer},
		);

		// then
		await expect.element(screen.getByText('Top panel')).toBeVisible();
		await expect.element(screen.getByText('Bottom panel')).toBeVisible();
	});

	it('should render the left panel when provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="process"
				leftPanel={<div>Filters</div>}
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
			/>,
		);

		// then
		await expect.element(screen.getByText('Filters')).toBeVisible();
	});

	it('should render additionalTopContent and footer when provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="decision"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				additionalTopContent={<div>Extra top content</div>}
				footer={<div>Footer content</div>}
			/>,
		);

		// then
		await expect.element(screen.getByText('Extra top content')).toBeVisible();
		await expect.element(screen.getByText('Footer content')).toBeVisible();
	});

	it('should not render a frame header by default', async () => {
		// given / when
		const screen = await render(
			<InstancesList type="process" topPanel={<div>Top</div>} bottomPanel={<div>Bottom</div>} />,
			{wrapper: SizedContainer},
		);

		// then
		await expect.element(screen.getByText('Top')).toBeVisible();
		await expect.element(screen.getByTestId('frame-container')).not.toBeInTheDocument();
	});

	it('should render the frame header title when a visible frame is provided', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="migrate"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				frame={{headerTitle: 'Migration preview'}}
			/>,
		);

		// then
		await expect.element(screen.getByTestId('frame-container')).toBeVisible();
		await expect.element(screen.getByText('Migration preview')).toBeVisible();
	});

	it('should hide the frame header when the frame is explicitly not visible', async () => {
		// given / when
		const screen = await render(
			<InstancesList
				type="migrate"
				topPanel={<div>Top</div>}
				bottomPanel={<div>Bottom</div>}
				frame={{headerTitle: 'Migration preview', isVisible: false}}
			/>,
			{wrapper: SizedContainer},
		);

		// then
		await expect.element(screen.getByText('Top')).toBeVisible();
		await expect.element(screen.getByText('Migration preview')).not.toBeInTheDocument();
	});
});
