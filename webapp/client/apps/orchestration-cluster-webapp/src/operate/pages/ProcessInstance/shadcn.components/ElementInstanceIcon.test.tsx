/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect, it} from 'vitest';
import {cleanup, render} from 'vitest-browser-react';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {ElementInstanceIcon} from './ElementInstanceIcon';

afterEach(async () => {
	await cleanup();
});

describe('ElementInstanceIcon (design system)', () => {
	it('should render a decorative svg icon for the process root', async () => {
		const screen = await render(<ElementInstanceIcon root />);

		expect(screen.container.querySelector('[data-icon="process-root"][aria-hidden="true"] svg')).not.toBeNull();
	});

	it('should render the generic task icon when the element is unknown', async () => {
		const screen = await render(<ElementInstanceIcon />);

		expect(screen.container.querySelector('[data-icon="task-undefined"] svg')).not.toBeNull();
	});

	it('should render the template icon image instead of the svg when the element defines one', async () => {
		const businessObject = {
			$type: 'bpmn:ServiceTask',
			$instanceOf: (type: string) => type === 'bpmn:Activity',
			get: () => 'data:image/svg+xml;base64,PHN2Zy8+',
		} as unknown as BusinessObject;

		const screen = await render(<ElementInstanceIcon businessObject={businessObject} />);

		expect(screen.container.querySelector('img')).toHaveAttribute('src', 'data:image/svg+xml;base64,PHN2Zy8+');
		expect(screen.container.querySelector('svg')).toBeNull();
	});
});
