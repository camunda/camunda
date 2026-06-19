/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {render} from 'vitest-browser-react';
import {describe, it, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {useContext} from 'react';
import {createPortal} from 'react-dom';
import {Diagram} from './index';
import {DiagramOverlayContext} from './DiagramOverlayContext';

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

const OVERLAY_TYPE = 'testOverlayType';

function OverlayPortals() {
	const overlays = useContext(DiagramOverlayContext);
	return overlays.map(({container, payload, elementId}) =>
		createPortal(<div>{String(payload)}</div>, container, elementId),
	);
}

function OverlayCount() {
	const overlays = useContext(DiagramOverlayContext);
	return <span data-testid="overlay-count">{overlays.length}</span>;
}

describe('<Diagram />', () => {
	it('should render diagram controls after XML import', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} />);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();
	});

	it('should call onElementSelection when a selectable element is clicked', async () => {
		const onElementSelection = vi.fn();
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} onElementSelection={onElementSelection} />,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();

		const element = document.querySelector<SVGElement>('[data-element-id="startEvent_1"]');
		await userEvent.click(element!);

		expect(onElementSelection).toHaveBeenCalledWith('startEvent_1', false);
	});

	it('should expose overlay containers via DiagramOverlayContext', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				overlaysData={[
					{
						payload: 'example overlay content',
						type: OVERLAY_TYPE,
						elementId: 'startEvent_1',
						position: {top: 0, left: 0},
					},
				]}
			>
				<OverlayPortals />
			</Diagram>,
		);

		await expect.element(screen.getByText('example overlay content')).toBeVisible();
	});

	it('should expose multiple overlays on different elements via DiagramOverlayContext', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				overlaysData={[
					{
						payload: 'start overlay',
						type: OVERLAY_TYPE,
						elementId: 'startEvent_1',
						position: {top: 0, left: 0},
					},
					{
						payload: 'end overlay',
						type: OVERLAY_TYPE,
						elementId: 'endEvent_1',
						position: {top: 0, left: 0},
					},
				]}
			>
				<OverlayPortals />
			</Diagram>,
		);

		await expect.element(screen.getByText('start overlay')).toBeVisible();
		await expect.element(screen.getByText('end overlay')).toBeVisible();
	});

	it('should not create overlay entries for non-existent elements', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				overlaysData={[
					{
						payload: 'ghost overlay',
						type: OVERLAY_TYPE,
						elementId: 'nonExistentElement',
						position: {top: 0, left: 0},
					},
				]}
			>
				<OverlayCount />
			</Diagram>,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByTestId('overlay-count')).toHaveTextContent('0');
	});

	it('should toggle minimap button label when shown and hidden', async () => {
		await render(
			<div style={{height: '400px', width: '800px'}}>
				<Diagram xml={BPMN_XML} />
			</div>,
		);

		await expect.element(document.querySelector<HTMLElement>('[aria-label="Show minimap"]')).toBeVisible();

		await userEvent.click(document.querySelector<HTMLElement>('[aria-label="Show minimap"]')!);

		await expect.element(document.querySelector<HTMLElement>('[aria-label="Hide minimap"]')).toBeVisible();

		await userEvent.click(document.querySelector<HTMLElement>('[aria-label="Hide minimap"]')!);

		await expect.element(document.querySelector<HTMLElement>('[aria-label="Show minimap"]')).toBeVisible();
	});

	it('should call onElementDoubleClick for elements with custom classes', async () => {
		const onElementDoubleClick = vi.fn();
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				customElementClasses={[['startEvent_1', 'my-class']]}
				onElementDoubleClick={onElementDoubleClick}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();

		const element = document.querySelector<SVGElement>('[data-element-id="startEvent_1"]');
		await userEvent.dblClick(element!);

		expect(onElementDoubleClick).toHaveBeenCalledWith('startEvent_1');
	});
});
