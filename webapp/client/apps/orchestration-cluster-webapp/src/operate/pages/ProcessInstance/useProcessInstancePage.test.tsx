/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {expect} from 'vitest';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {ProcessInstanceContext, useProcessInstancePage} from './useProcessInstancePage';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {renderWithRouter} from '#/vitest-modules/render-with-router';

it('should expose the instance and selection to nested content', async () => {
	function Preview() {
		const {processInstanceId, processInstance, search, selection} = useProcessInstancePage();
		return (
			<output>
				{processInstanceId} {processInstance.processDefinitionName} {search.elementId} {selection.elementInstanceKey}
			</output>
		);
	}
	const processInstance = createProcessInstance({processInstanceKey: '42', processDefinitionName: 'Orders'});
	const screen = await render(
		<ProcessInstanceContext
			value={{
				processInstanceId: processInstance.processInstanceKey,
				processInstance,
				search: {elementId: 'service-task'},
				selection: {elementInstanceKey: '101'},
			}}
		>
			<Preview />
		</ProcessInstanceContext>,
	);
	await expect.element(screen.getByRole('status')).toHaveTextContent('42 Orders service-task 101');
});

it('should expose only selection fields while preserving unrelated search context', async () => {
	const selection = {
		elementId: 'service-task',
		elementInstanceKey: 'placeholder',
		isMultiInstanceBody: true,
		isPlaceholder: true,
		anchorElementId: 'anchor',
	};
	const search = {...selection, tenantId: 'tenant-a', elementSearch: 'User Task'};
	const instance = createProcessInstance();
	function Preview() {
		const context = useProcessInstancePage();
		return <output>{JSON.stringify({selection: context.selection, search: context.search})}</output>;
	}
	const screen = await renderWithRouter(
		() => (
			<ProcessInstancePageProvider
				processInstanceId={instance.processInstanceKey}
				processInstance={instance}
				search={search}
			>
				<Preview />
			</ProcessInstancePageProvider>
		),
		{path: '/operate/processes/$processInstanceId/variables'},
	);
	await expect.element(screen.getByRole('status')).toHaveTextContent(JSON.stringify({selection, search}));
});
