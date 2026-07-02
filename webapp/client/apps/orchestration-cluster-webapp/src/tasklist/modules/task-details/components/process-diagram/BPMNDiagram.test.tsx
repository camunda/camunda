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
import {BPMNDiagram} from './BPMNDiagram';
import {BPMN_XML, UPDATED_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
	return <div style={{height: '400px', width: '800px'}}>{children}</div>;
};

describe('<BPMNDiagram />', () => {
	it('should make diagram controls available after the BPMN diagram loads', async () => {
		const screen = await render(<BPMNDiagram xml={BPMN_XML} highlightActivity="task-1" />, {wrapper: Wrapper});

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();
	});

	it('should render a process diagram from valid BPMN XML', async () => {
		const screen = await render(<BPMNDiagram xml={BPMN_XML} highlightActivity="task-1" />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();
	});

	it('should continue showing usable diagram controls when the highlighted task is not present in the diagram', async () => {
		const screen = await render(<BPMNDiagram xml={BPMN_XML} highlightActivity="missing-task" />, {wrapper: Wrapper});

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
	});

	it('should update the displayed diagram when a different BPMN XML is provided', async () => {
		const screen = await render(<BPMNDiagram xml={BPMN_XML} highlightActivity="task-1" />, {wrapper: Wrapper});

		await expect.element(screen.getByText('Review invoice')).toBeVisible();

		screen.rerender(<BPMNDiagram xml={UPDATED_BPMN_XML} highlightActivity="task-2" />);

		await expect.element(screen.getByText('Approve payment')).toBeVisible();
	});
});
