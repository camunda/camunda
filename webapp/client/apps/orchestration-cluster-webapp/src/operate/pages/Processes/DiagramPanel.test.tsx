/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {renderWithRouter} from '#/vitest-modules/render-with-router';
import {
	mockGetProcessDefinitionXmlEndpoint,
	mockGetProcessDefinitionStatisticsEndpoint,
} from '#/shared-test-modules/mock-handlers';
import {createProcessDefinition} from '#/shared-test-modules/api-mocks/process-definitions';
import {
	createProcessDefinitionStatistic,
	createGetProcessDefinitionStatisticsResponse,
} from '#/shared-test-modules/api-mocks/process-definition-statistics';
import {DiagramPanel} from './DiagramPanel';

function renderDiagramPanel(props: React.ComponentProps<typeof DiagramPanel>) {
	return renderWithRouter(() => <DiagramPanel {...props} />, {path: '/operate/processes'});
}

const BPMN_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="false">
    <bpmn:startEvent id="startEvent_1" />
    <bpmn:endEvent id="endEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="startEvent_1_di" bpmnElement="startEvent_1">
        <dc:Bounds x="152" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endEvent_1_di" bpmnElement="endEvent_1">
        <dc:Bounds x="302" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const DEFINITION = createProcessDefinition({processDefinitionKey: '2251799813685279'});

describe('<DiagramPanel />', () => {
	it('shows an empty message when no process is selected', async () => {
		const screen = await renderDiagramPanel({
			processDefinitionSelection: {kind: 'no-match'},
			onElementSelection: vi.fn(),
			active: true,
			incidents: true,
			completed: false,
			canceled: false,
		});

		await expect.element(screen.getByText('There is no Process selected')).toBeVisible();
	});

	it('shows an empty message when multiple versions are selected', async () => {
		const screen = await renderDiagramPanel({
			processDefinitionSelection: {
				kind: 'all-versions',
				definition: {name: 'Order Process', processDefinitionId: 'order-process'},
			},
			onElementSelection: vi.fn(),
			active: true,
			incidents: true,
			completed: false,
			canceled: false,
		});

		await expect
			.element(screen.getByText('There is more than one Version selected for Process "Order Process"'))
			.toBeVisible();
	});

	it('renders the diagram and a statistics overlay for a single selected version', async ({worker}) => {
		worker.use(
			mockGetProcessDefinitionXmlEndpoint({successResponse: HttpResponse.text(BPMN_XML)}),
			mockGetProcessDefinitionStatisticsEndpoint({
				successResponse: HttpResponse.json(
					createGetProcessDefinitionStatisticsResponse([
						createProcessDefinitionStatistic({elementId: 'startEvent_1', active: 3}),
					]),
				),
			}),
		);

		const screen = await renderDiagramPanel({
			processDefinitionSelection: {kind: 'single-version', definition: DEFINITION},
			onElementSelection: vi.fn(),
			active: true,
			incidents: true,
			completed: false,
			canceled: false,
		});

		await expect.element(screen.getByTestId('state-overlay-startEvent_1-active')).toHaveTextContent('3');
	});
});
