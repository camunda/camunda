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
import {ProcessDiagramView} from './ProcessDiagramView';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
	return <div style={{height: '400px', width: '800px'}}>{children}</div>;
};

describe('<ProcessDiagramView />', () => {
	it('should show the process name and version', async () => {
		const screen = await render(
			<ProcessDiagramView xml={BPMN_XML} elementId="task-1" processName="Invoice process" processVersion={3} />,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByText('Invoice process')).toBeVisible();
		await expect.element(screen.getByText('Version: 3')).toBeVisible();
	});

	it('should allow users to interact with the rendered process diagram', async () => {
		const screen = await render(
			<ProcessDiagramView xml={BPMN_XML} elementId="task-1" processName="Invoice process" processVersion={3} />,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();
	});

	it('should show the provided BPMN process diagram', async () => {
		const screen = await render(
			<ProcessDiagramView xml={BPMN_XML} elementId="task-1" processName="Invoice process" processVersion={3} />,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
	});
});
