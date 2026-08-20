/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, expect} from 'vitest';
import {createUserTask} from '#/shared-test-modules/api-mocks/user-tasks';
import {it} from '#/vitest-modules/test-extend';
import {TaskDetailsProcessPage} from './TaskDetailsProcessPage';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';

const Wrapper: React.FC<{children: React.ReactNode}> = ({children}) => {
	return <div style={{display: 'flex', height: '400px', width: '800px'}}>{children}</div>;
};

describe('<TaskDetailsProcessPage />', () => {
	it('should show the task process metadata and diagram', async () => {
		const screen = await render(
			<TaskDetailsProcessPage
				task={createUserTask({processName: 'Invoice process', processDefinitionVersion: 7})}
				processXml={BPMN_XML}
			/>,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByText('Invoice process')).toBeVisible();
		await expect.element(screen.getByText('Version: 7')).toBeVisible();
		await expect.element(screen.getByText('Review invoice')).toBeVisible();
	});

	it('should show the process definition ID when the task has no process name', async () => {
		const screen = await render(
			<TaskDetailsProcessPage
				task={createUserTask({processName: null, processDefinitionId: 'invoice-process'})}
				processXml={BPMN_XML}
			/>,
			{wrapper: Wrapper},
		);

		await expect.element(screen.getByText('invoice-process')).toBeVisible();
	});
});
