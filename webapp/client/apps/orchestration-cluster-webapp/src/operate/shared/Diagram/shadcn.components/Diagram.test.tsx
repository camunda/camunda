/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import '#/shared/theme/tailwind.css';
import {render} from 'vitest-browser-react';
import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {it} from '#/vitest-modules/test-extend';
import {useContext, useEffect, useState} from 'react';
import {createPortal} from 'react-dom';
import {Diagram} from './Diagram';
import {DiagramOverlayContext} from '../DiagramOverlayContext';

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

const NAMED_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" id="Definitions_2" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_2" isExecutable="false">
    <bpmn:startEvent id="startEvent_1" name="Begin" />
    <bpmn:serviceTask id="task_1" name="Pay" />
    <bpmn:dataObjectReference id="dataObject_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_2">
    <bpmndi:BPMNPlane id="BPMNPlane_2" bpmnElement="Process_2">
      <bpmndi:BPMNShape id="startEvent_1_di" bpmnElement="startEvent_1">
        <dc:Bounds x="152" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1">
        <dc:Bounds x="120" y="220" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="dataObject_1_di" bpmnElement="dataObject_1">
        <dc:Bounds x="400" y="82" width="36" height="50" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

const XML_WITHOUT_LAYOUT = BPMN_XML.slice(0, BPMN_XML.indexOf('<bpmndi:BPMNDiagram')) + '</bpmn:definitions>';

const FLOW_XML = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" id="Definitions_3" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_3" isExecutable="false">
    <bpmn:startEvent id="startEvent_1" />
    <bpmn:task id="task_1" />
    <bpmn:endEvent id="endEvent_1" />
    <bpmn:sequenceFlow id="flow_1" sourceRef="startEvent_1" targetRef="task_1" />
    <bpmn:sequenceFlow id="flow_2" sourceRef="task_1" targetRef="endEvent_1" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_3">
    <bpmndi:BPMNPlane id="BPMNPlane_3" bpmnElement="Process_3">
      <bpmndi:BPMNShape id="startEvent_1_di" bpmnElement="startEvent_1">
        <dc:Bounds x="152" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="task_1_di" bpmnElement="task_1">
        <dc:Bounds x="250" y="60" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="endEvent_1_di" bpmnElement="endEvent_1">
        <dc:Bounds x="412" y="82" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_1_di" bpmnElement="flow_1">
        <di:waypoint x="188" y="100" />
        <di:waypoint x="250" y="100" />
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_2_di" bpmnElement="flow_2">
        <di:waypoint x="350" y="100" />
        <di:waypoint x="412" y="100" />
      </bpmndi:BPMNEdge>
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
	return (
		<span role="status" aria-label="Overlay count">
			{overlays.length}
		</span>
	);
}

function SelectedElementOverlay({selectedElementRef}: {selectedElementRef?: SVGElement}) {
	return <span>{`Overlay for ${selectedElementRef?.getAttribute('data-element-id')}`}</span>;
}

describe('<Diagram />', () => {
	it('should render diagram controls after XML import', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} />);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await expect.element(screen.getByRole('button', {name: 'Zoom out diagram'})).toBeVisible();
	});

	it('should resolve the renderer colors from design system tokens', async () => {
		const screen = await render(
			<div className="c4-ui">
				<Diagram xml={BPMN_XML} />
			</div>,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		const diagram = screen.getByRole('region', {name: 'Diagram'}).element();

		expect(getComputedStyle(diagram).getPropertyValue('--cds-text-primary')).not.toBe('');
		expect(getComputedStyle(diagram).getPropertyValue('--cds-layer-01')).not.toBe('');
	});

	it('should show BPMN branding after XML import', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} />);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.element(screen.getByTitle('Powered by bpmn.io')).toBeVisible();
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

	it('should render the selected element overlay for the selected element', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				selectedElementIds={['endEvent_1']}
				selectedElementOverlay={<SelectedElementOverlay />}
			/>,
		);

		await expect.element(screen.getByText('Overlay for endEvent_1')).toBeVisible();
	});

	it('should outline a programmatically selected element', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				selectedElementIds={['endEvent_1']}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Reset diagram zoom'})).toBeVisible();
		await expect.poll(() => document.querySelector('[data-element-id="endEvent_1"] .djs-outline')).not.toBeNull();
	});

	it('should keep focus on a diagram control when overlays update', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				overlaysData={[{type: OVERLAY_TYPE, elementId: 'startEvent_1', payload: 'a', position: {top: 0, left: 0}}]}
			/>,
		);
		const zoomIn = screen.getByRole('button', {name: 'Zoom in diagram'});
		await expect.element(zoomIn).toBeVisible();
		(zoomIn.element() as HTMLElement).focus();

		await screen.rerender(
			<Diagram
				xml={BPMN_XML}
				overlaysData={[{type: OVERLAY_TYPE, elementId: 'startEvent_1', payload: 'b', position: {top: 0, left: 0}}]}
			/>,
		);

		await expect.element(zoomIn).toHaveFocus();
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
		await expect.element(screen.getByRole('status', {name: 'Overlay count'})).toHaveTextContent('0');
	});

	it('should toggle minimap button label when shown and hidden', async () => {
		const screen = await render(
			<div style={{height: '400px', width: '800px'}}>
				<Diagram xml={BPMN_XML} />
			</div>,
		);

		await expect.element(screen.getByRole('button', {name: 'Show minimap'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Show minimap'}));

		await expect.element(screen.getByRole('button', {name: 'Hide minimap'})).toBeVisible();

		await userEvent.click(screen.getByRole('button', {name: 'Hide minimap'}));

		await expect.element(screen.getByRole('button', {name: 'Show minimap'})).toBeVisible();
	});

	it('should keep the minimap open when selection changes without an XML change', async () => {
		const screen = await render(
			<div style={{height: '400px', width: '800px'}}>
				<Diagram xml={BPMN_XML} selectableElements={['startEvent_1', 'endEvent_1']} selectedElementIds={[]} />
			</div>,
		);

		await userEvent.click(screen.getByRole('button', {name: 'Show minimap'}));
		await expect.element(screen.getByRole('button', {name: 'Hide minimap'})).toBeVisible();

		await screen.rerender(
			<div style={{height: '400px', width: '800px'}}>
				<Diagram
					xml={BPMN_XML}
					selectableElements={['startEvent_1', 'endEvent_1']}
					selectedElementIds={['endEvent_1']}
				/>
			</div>,
		);

		await expect.poll(() => document.querySelector('[data-element-id="endEvent_1"] .djs-outline')).not.toBeNull();
		await expect.element(screen.getByRole('button', {name: 'Hide minimap'})).toBeVisible();
	});

	it('should keep the focused control mounted when selection changes without an XML change', async () => {
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1', 'endEvent_1']} selectedElementIds={[]} />,
		);
		const zoomIn = screen.getByRole('button', {name: 'Zoom in diagram'});
		await expect.element(zoomIn).toBeVisible();
		(zoomIn.element() as HTMLElement).focus();

		await screen.rerender(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				selectedElementIds={['endEvent_1']}
			/>,
		);

		await expect.poll(() => document.querySelector('[data-element-id="endEvent_1"] .djs-outline')).not.toBeNull();
		expect(document.activeElement).toBe(zoomIn.element());
	});

	it('should apply the latest selection when it changes while the XML import is pending', async () => {
		function SelectsOnMount() {
			const [selectedElementIds, setSelectedElementIds] = useState<string[]>([]);
			useEffect(() => {
				// Selecting right after mount, while the first XML import is still pending, is the scenario under test.
				// eslint-disable-next-line react-hooks/set-state-in-effect
				setSelectedElementIds(['endEvent_1']);
			}, []);

			return (
				<Diagram
					xml={BPMN_XML}
					selectableElements={['startEvent_1', 'endEvent_1']}
					selectedElementIds={selectedElementIds}
				/>
			);
		}

		await render(<SelectsOnMount />);

		await expect.poll(() => document.querySelector('[data-element-id="endEvent_1"] .djs-outline')).not.toBeNull();
	});

	it('should report a failed import once and not mark the diagram ready when props change while it is pending', async () => {
		const onRenderError = vi.fn();
		function SelectsOnMount() {
			const [selectedElementIds, setSelectedElementIds] = useState<string[]>([]);
			useEffect(() => {
				// Selecting right after mount, while the first XML import is still pending, is the scenario under test.
				// eslint-disable-next-line react-hooks/set-state-in-effect
				setSelectedElementIds(['endEvent_1']);
			}, []);

			return <Diagram xml={XML_WITHOUT_LAYOUT} selectedElementIds={selectedElementIds} onRenderError={onRenderError} />;
		}

		const screen = await render(<SelectsOnMount />);

		await expect.poll(() => onRenderError).toHaveBeenCalledTimes(1);
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).not.toBeInTheDocument();
	});

	it('should bind the stable callbacks to the replacement viewer after a failed import', async () => {
		const onElementSelection = vi.fn();
		const onRenderError = vi.fn();
		const screen = await render(
			<Diagram xml={XML_WITHOUT_LAYOUT} onElementSelection={onElementSelection} onRenderError={onRenderError} />,
		);
		await expect.poll(() => onRenderError).toHaveBeenCalledTimes(1);

		await screen.rerender(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				onElementSelection={onElementSelection}
				onRenderError={onRenderError}
			/>,
		);
		await expect.element(screen.getByRole('button', {name: 'Zoom in diagram'})).toBeVisible();
		await userEvent.click(document.querySelector<SVGElement>('[data-element-id="startEvent_1"]')!);

		expect(onElementSelection).toHaveBeenCalledWith('startEvent_1', false);
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

	it('should describe how to navigate the diagram with the keyboard', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} />);

		await expect
			.element(screen.getByRole('button', {name: 'start event', exact: true}))
			.toHaveAccessibleDescription('Use the arrow keys to move between elements and Enter to select.');
		await expect
			.element(screen.getByRole('region', {name: 'Diagram'}))
			.toHaveAccessibleDescription('Use the arrow keys to move between elements and Enter to select.');
	});

	it('should use the provided aria label for the diagram region', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} ariaLabel="Order process" />);

		await expect.element(screen.getByRole('region', {name: 'Order process'})).toBeVisible();
	});

	it('should name non-selectable elements even without tooltip text', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} />);

		await expect.element(screen.getByRole('img', {name: 'end event', exact: true})).toBeInTheDocument();
	});

	it('should append the tooltip text to the name of non-selectable elements', async () => {
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} nonSelectableNodeTooltipText="Not selectable" />,
		);

		await expect.element(screen.getByRole('img', {name: 'end event, Not selectable', exact: true})).toBeInTheDocument();
	});

	it('should name elements by name and type, falling back to a readable type for unknown ones', async () => {
		const screen = await render(
			<Diagram
				xml={NAMED_XML}
				selectableElements={['startEvent_1', 'task_1']}
				nonSelectableNodeTooltipText="Not selectable"
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'Begin (start event)', exact: true})).toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'Pay (service task)', exact: true})).toBeInTheDocument();
		await expect
			.element(screen.getByRole('img', {name: 'data object reference, Not selectable', exact: true}))
			.toBeInTheDocument();
	});

	it('should show the tooltip text when hovering a non-selectable element', async () => {
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} nonSelectableNodeTooltipText="Not selectable" />,
		);

		await userEvent.hover(screen.getByRole('img', {name: 'end event, Not selectable', exact: true}));

		await expect.element(screen.getByRole('tooltip', {name: 'Not selectable'})).toBeVisible();
	});

	it('should expose the selection state of selectable elements', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				selectedElementIds={['startEvent_1']}
			/>,
		);

		await expect.element(screen.getByRole('button', {name: 'start event', pressed: true})).toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'end event', pressed: false})).toBeInTheDocument();
	});

	it('should drop button semantics from elements that stop being selectable', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} />);
		await expect.element(screen.getByRole('button', {name: 'start event', exact: true})).toBeInTheDocument();

		await screen.rerender(<Diagram xml={BPMN_XML} selectableElements={['endEvent_1']} />);

		await expect.element(screen.getByRole('button', {name: 'end event', exact: true})).toBeInTheDocument();
		await expect.element(screen.getByRole('button', {name: 'start event', exact: true})).not.toBeInTheDocument();
	});

	it('should drop the restriction announcement when no elements are restricted anymore', async () => {
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} nonSelectableNodeTooltipText="Not selectable" />,
		);
		await expect.element(screen.getByRole('img', {name: 'end event, Not selectable', exact: true})).toBeInTheDocument();

		await screen.rerender(<Diagram xml={BPMN_XML} />);

		await expect.element(screen.getByRole('img', {name: 'end event, Not selectable'})).not.toBeInTheDocument();
		await expect.element(screen.getByRole('img', {name: 'end event'})).not.toBeInTheDocument();
	});

	it('should move focus between elements with the arrow keys', async () => {
		const screen = await render(
			<Diagram xml={NAMED_XML} selectableElements={['startEvent_1', 'task_1', 'dataObject_1']} />,
		);
		const start = screen.getByRole('button', {name: 'Begin (start event)', exact: true});
		const task = screen.getByRole('button', {name: 'Pay (service task)', exact: true});
		const dataObject = screen.getByRole('button', {name: 'data object reference', exact: true});
		await expect.element(start).toBeInTheDocument();

		await userEvent.tab();
		await expect.element(start).toHaveFocus();

		await userEvent.keyboard('{ArrowUp}');
		await expect.element(start).toHaveFocus();

		await userEvent.keyboard('{ArrowDown}');
		await expect.element(task).toHaveFocus();

		await userEvent.keyboard('{ArrowUp}');
		await expect.element(start).toHaveFocus();

		await userEvent.keyboard('{ArrowRight}');
		await expect.element(dataObject).toHaveFocus();

		await userEvent.keyboard('{ArrowLeft}');
		await expect.element(start).toHaveFocus();
	});

	it('should keep a single tab stop so that Tab leaves the diagram elements', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1', 'endEvent_1']} />);
		const start = screen.getByRole('button', {name: 'start event', exact: true});
		await expect.element(start).toBeInTheDocument();

		await userEvent.tab();
		await expect.element(start).toHaveFocus();
		await userEvent.tab();

		await expect.element(start).not.toHaveFocus();
		await expect.element(screen.getByRole('button', {name: 'end event', exact: true})).not.toHaveFocus();
	});

	it.for([{key: '{Enter}'}, {key: ' '}])('should select the focused element when pressing "$key"', async ({key}) => {
		const onElementSelection = vi.fn();
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} onElementSelection={onElementSelection} />,
		);
		await expect.element(screen.getByRole('button', {name: 'start event', exact: true})).toBeInTheDocument();
		await userEvent.tab();

		await userEvent.keyboard(key);

		expect(onElementSelection).toHaveBeenCalledWith('startEvent_1', false);
	});

	it('should keep focus and the tab stop on the focused element when the diagram re-renders', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1', 'endEvent_1']} />);
		const end = screen.getByRole('button', {name: 'end event', exact: true});
		await expect.element(end).toBeInTheDocument();
		await userEvent.tab();
		await userEvent.keyboard('{ArrowRight}');
		await expect.element(end).toHaveFocus();

		await screen.rerender(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				customElementClasses={[['startEvent_1', 'my-class']]}
			/>,
		);

		await expect.element(end).toHaveFocus();
		await userEvent.tab();
		await userEvent.tab({shift: true});
		await expect.element(end).toHaveFocus();
	});

	it('should show the keyboard hint while an element has keyboard focus', async () => {
		const screen = await render(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} />);
		const hint = screen.getByText('Use the arrow keys to move between elements and Enter to select.');
		await expect.element(screen.getByRole('button', {name: 'start event', exact: true})).toBeInTheDocument();

		await userEvent.tab();

		await expect.element(hint).toBeVisible();
	});

	it('should reset the color of sequence flows that are no longer highlighted', async () => {
		const flowStroke = (id: string) =>
			getComputedStyle(document.querySelector(`[data-element-id="${id}"] path`)!).stroke;
		const renderDiagram = (highlightedSequenceFlows: string[]) => (
			<div className="c4-ui">
				<Diagram xml={FLOW_XML} highlightedSequenceFlows={highlightedSequenceFlows} />
			</div>
		);
		const screen = await render(renderDiagram(['flow_1']));
		await expect.poll(() => flowStroke('flow_2')).not.toBe('none');
		const defaultStroke = flowStroke('flow_2');
		await expect.poll(() => flowStroke('flow_1')).not.toBe(defaultStroke);
		const highlightedStroke = flowStroke('flow_1');

		await screen.rerender(renderDiagram(['flow_2']));

		await expect.poll(() => flowStroke('flow_2')).toBe(highlightedStroke);
		expect(flowStroke('flow_1')).toBe(defaultStroke);
	});

	it('should announce the drill-down shortcut in the name of drill-down elements', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1', 'endEvent_1']}
				customElementClasses={[['startEvent_1', 'op-drilldown']]}
			/>,
		);

		await expect
			.element(screen.getByRole('button', {name: 'start event, Press Shift+Enter to drill down.', exact: true}))
			.toHaveAttribute('aria-keyshortcuts', 'Shift+Enter');
		await expect.element(screen.getByRole('button', {name: 'end event', exact: true})).toBeInTheDocument();
	});

	it('should announce that a drill-down element is loading', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1']}
				customElementClasses={[
					['startEvent_1', 'op-drilldown'],
					['startEvent_1', 'op-drilldown-loading'],
				]}
			/>,
		);

		await expect
			.element(
				screen.getByRole('button', {name: 'start event, Loading, Press Shift+Enter to drill down.', exact: true}),
			)
			.toBeInTheDocument();
	});

	it('should drop the drill-down announcement when drill-down is no longer set', async () => {
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1']}
				customElementClasses={[['startEvent_1', 'op-drilldown']]}
			/>,
		);
		await expect
			.element(screen.getByRole('button', {name: 'start event, Press Shift+Enter to drill down.', exact: true}))
			.toBeInTheDocument();

		await screen.rerender(<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} customElementClasses={[]} />);

		const element = screen.getByRole('button', {name: 'start event', exact: true});
		await expect.element(element).toBeInTheDocument();
		await expect.element(element).not.toHaveAttribute('aria-keyshortcuts');
	});

	it('should drill down on Shift+Enter without selecting the element', async () => {
		const onElementDoubleClick = vi.fn();
		const onElementSelection = vi.fn();
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1']}
				customElementClasses={[['startEvent_1', 'op-drilldown']]}
				onElementDoubleClick={onElementDoubleClick}
				onElementSelection={onElementSelection}
			/>,
		);
		await expect.element(screen.getByRole('button', {name: /Press Shift\+Enter/})).toBeInTheDocument();

		await userEvent.tab();
		await userEvent.keyboard('{Shift>}{Enter}{/Shift}');

		expect(onElementDoubleClick).toHaveBeenCalledExactlyOnceWith('startEvent_1');
		expect(onElementSelection).not.toHaveBeenCalled();
	});

	it('should select a drill-down element on Enter without drilling down', async () => {
		const onElementDoubleClick = vi.fn();
		const onElementSelection = vi.fn();
		const screen = await render(
			<Diagram
				xml={BPMN_XML}
				selectableElements={['startEvent_1']}
				customElementClasses={[['startEvent_1', 'op-drilldown']]}
				onElementDoubleClick={onElementDoubleClick}
				onElementSelection={onElementSelection}
			/>,
		);
		await expect.element(screen.getByRole('button', {name: /Press Shift\+Enter/})).toBeInTheDocument();

		await userEvent.tab();
		await userEvent.keyboard('{Enter}');

		expect(onElementSelection).toHaveBeenCalledOnce();
		expect(onElementDoubleClick).not.toHaveBeenCalled();
	});

	it('should not drill down on Shift+Enter for elements without drill-down', async () => {
		const onElementDoubleClick = vi.fn();
		const screen = await render(
			<Diagram xml={BPMN_XML} selectableElements={['startEvent_1']} onElementDoubleClick={onElementDoubleClick} />,
		);
		await expect.element(screen.getByRole('button', {name: 'start event', exact: true})).toBeInTheDocument();

		await userEvent.tab();
		await userEvent.keyboard('{Shift>}{Enter}{/Shift}');

		expect(onElementDoubleClick).not.toHaveBeenCalled();
	});
});
