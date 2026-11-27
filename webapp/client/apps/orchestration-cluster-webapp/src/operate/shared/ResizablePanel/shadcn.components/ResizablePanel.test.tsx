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
import {ResizablePanel, SplitDirection} from './ResizablePanel';

describe('<ResizablePanel />', () => {
	it('should render both children', async () => {
		const screen = await render(
			<div style={{width: '400px', height: '400px'}}>
				<ResizablePanel direction={SplitDirection.Horizontal} panelId="test-panel">
					<div data-testid="left">left</div>
					<div data-testid="right">right</div>
				</ResizablePanel>
			</div>,
		);

		await expect.element(screen.getByTestId('left')).toBeVisible();
		await expect.element(screen.getByTestId('right')).toBeVisible();
	});
});
