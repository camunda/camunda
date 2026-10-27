/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {afterEach, describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {cleanup} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {useRouterState} from '@tanstack/react-router';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockQueryElementInstancesEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createQueryElementInstancesResponse} from '#/shared-test-modules/api-mocks/element-instances';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {ProcessInstancePageProvider} from './ProcessInstancePageProvider';
import {InputMappingsTab, OutputMappingsTab} from './InputOutputMappings';
import {processInstanceSearchSchema} from './processInstanceSearch';

const PROCESS_INSTANCE_ID = '2251799813685280';
const PROCESS_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">
  <bpmn:process id="my-process">
    <bpmn:serviceTask id="mapped-task">
      <bpmn:extensionElements>
        <zeebe:ioMapping>
          <zeebe:input source="=orderId" target="localOrderId" />
          <zeebe:input source="=amount * 2" target="doubledAmount" />
          <zeebe:output source="=result" target="processResult" />
        </zeebe:ioMapping>
      </bpmn:extensionElements>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="plain-task" />
  </bpmn:process>
</bpmn:definitions>`;

afterEach(async () => {
	await cleanup();
	localStorage.clear();
});

function renderMappings(Tab: typeof InputMappingsTab, elementId: string) {
	function Mappings() {
		const routeSearch = useRouterState({select: (state) => state.location.search});
		return (
			<div style={{height: '400px', display: 'flex', flexDirection: 'column'}}>
				<ProcessInstancePageProvider
					processInstanceId={PROCESS_INSTANCE_ID}
					processInstance={createProcessInstance({processInstanceKey: PROCESS_INSTANCE_ID})}
					search={processInstanceSearchSchema.parse(routeSearch)}
				>
					<Tab />
				</ProcessInstancePageProvider>
			</div>
		);
	}

	return renderWithRouter(Mappings, {
		path: '/operate/processes/$processInstanceId/$',
		initialEntry: `/operate/processes/${PROCESS_INSTANCE_ID}/mappings${elementId === '' ? '' : `?elementId=${elementId}`}`,
	});
}

describe('Input and output mappings', () => {
	it.beforeEach(({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(PROCESS_XML)}),
			mockQueryElementInstancesEndpoint({successResponse: HttpResponse.json(createQueryElementInstancesResponse())}),
		);
	});

	it('should list the input mappings of the selected element', async () => {
		const screen = await renderMappings(InputMappingsTab, 'mapped-task');

		await expect.element(screen.getByText('Local Variable Name')).toBeVisible();
		await expect.element(screen.getByText('Variable Assignment Value')).toBeVisible();
		await expect.element(screen.getByText('localOrderId')).toBeVisible();
		await expect.element(screen.getByText('=orderId')).toBeVisible();
		await expect.element(screen.getByText('doubledAmount')).toBeVisible();
		await expect.element(screen.getByText('=amount * 2')).toBeVisible();
		await expect.element(screen.getByText('processResult')).not.toBeInTheDocument();
	});

	it('should list the output mappings of the selected element', async () => {
		const screen = await renderMappings(OutputMappingsTab, 'mapped-task');

		await expect.element(screen.getByText('Process Variable Name')).toBeVisible();
		await expect.element(screen.getByText('processResult')).toBeVisible();
		await expect.element(screen.getByText('=result')).toBeVisible();
		await expect.element(screen.getByText('localOrderId')).not.toBeInTheDocument();
	});

	it('should show an empty message for an element without mappings', async () => {
		const screen = await renderMappings(InputMappingsTab, 'plain-task');

		await expect.element(screen.getByText('No Input Mappings defined')).toBeVisible();
	});

	it('should show an empty message for output mappings of an element without mappings', async () => {
		const screen = await renderMappings(OutputMappingsTab, 'plain-task');

		await expect.element(screen.getByText('No Output Mappings defined')).toBeVisible();
	});

	it('should render nothing without a selected element', async () => {
		const screen = await renderMappings(InputMappingsTab, '');

		await expect.element(screen.getByText('No Input Mappings defined')).not.toBeInTheDocument();
		await expect
			.element(screen.getByText('Input mappings are defined while modelling the diagram.'))
			.not.toBeInTheDocument();
	});

	it('should explain the mappings until the banner is closed, and keep it closed for the next visit', async () => {
		const screen = await renderMappings(InputMappingsTab, 'mapped-task');
		await expect.element(screen.getByText('Input mappings are defined while modelling the diagram.')).toBeVisible();
		await expect
			.element(screen.getByRole('link', {name: 'Learn more'}))
			.toHaveAttribute(
				'href',
				'https://docs.camunda.io/docs/components/concepts/variables/#inputoutput-variable-mappings',
			);

		await userEvent.click(screen.getByRole('button', {name: 'Close'}));

		await expect
			.element(screen.getByText('Input mappings are defined while modelling the diagram.'))
			.not.toBeInTheDocument();
		await cleanup();
		const revisited = await renderMappings(InputMappingsTab, 'mapped-task');
		await expect.element(revisited.getByText('localOrderId')).toBeVisible();
		await expect
			.element(revisited.getByText('Input mappings are defined while modelling the diagram.'))
			.not.toBeInTheDocument();
	});

	it('should keep the output banner when only the input banner was closed', async () => {
		localStorage.setItem('operate.hideInputMappingsHelperBanner', 'true');
		const screen = await renderMappings(OutputMappingsTab, 'mapped-task');

		await expect.element(screen.getByText('Output mappings are defined while modelling the diagram.')).toBeVisible();
	});

	it('should keep the banner closed when it was closed in the legacy app', async () => {
		localStorage.setItem('sharedState', JSON.stringify({hideOutputMappingsHelperBanner: true}));
		const screen = await renderMappings(OutputMappingsTab, 'mapped-task');

		await expect.element(screen.getByText('processResult')).toBeVisible();
		await expect
			.element(screen.getByText('Output mappings are defined while modelling the diagram.'))
			.not.toBeInTheDocument();
	});
});
