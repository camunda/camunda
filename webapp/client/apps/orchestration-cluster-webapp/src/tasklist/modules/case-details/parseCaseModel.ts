/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const BPMN_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const BPMNDI_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/DI';
const DC_NAMESPACE = 'http://www.omg.org/spec/DD/20100524/DC';

const FLOW_NODE_TYPES = new Set([
	'startEvent',
	'endEvent',
	'intermediateCatchEvent',
	'intermediateThrowEvent',
	'boundaryEvent',
	'task',
	'userTask',
	'serviceTask',
	'scriptTask',
	'sendTask',
	'receiveTask',
	'manualTask',
	'businessRuleTask',
	'callActivity',
	'subProcess',
	'adHocSubProcess',
	'transaction',
	'exclusiveGateway',
	'parallelGateway',
	'inclusiveGateway',
	'eventBasedGateway',
	'complexGateway',
]);

type Bounds = {
	x: number;
	y: number;
	width: number;
	height: number;
};

type Milestone = {
	id: string;
	label: string;
	elementIds: string[];
};

type CaseModel = {
	milestones: Milestone[];
	elementNames: Map<string, string>;
};

const EMPTY_CASE_MODEL: CaseModel = {milestones: [], elementNames: new Map()};

function getChildElements(parent: Element, namespace: string, localName: string): Element[] {
	return Array.from(parent.getElementsByTagNameNS(namespace, localName));
}

function getPlaneShapes(document: Document, processId: string): Map<string, Bounds> {
	const collaborationIds = getChildElements(document.documentElement, BPMN_NAMESPACE, 'participant')
		.filter((participant) => participant.getAttribute('processRef') === processId)
		.flatMap((participant) => participant.parentElement?.getAttribute('id') ?? []);
	const plane = getChildElements(document.documentElement, BPMNDI_NAMESPACE, 'BPMNPlane').find((candidate) => {
		const bpmnElement = candidate.getAttribute('bpmnElement') ?? '';
		return bpmnElement === processId || collaborationIds.includes(bpmnElement);
	});
	const shapes = new Map<string, Bounds>();

	for (const shape of plane === undefined ? [] : getChildElements(plane, BPMNDI_NAMESPACE, 'BPMNShape')) {
		const elementId = shape.getAttribute('bpmnElement');
		const bounds = getChildElements(shape, DC_NAMESPACE, 'Bounds')[0];

		if (elementId === null || bounds === undefined) {
			continue;
		}

		shapes.set(elementId, {
			x: Number(bounds.getAttribute('x')),
			y: Number(bounds.getAttribute('y')),
			width: Number(bounds.getAttribute('width')),
			height: Number(bounds.getAttribute('height')),
		});
	}

	return shapes;
}

function getFlowDistances(process: Element): Map<string, number> {
	const outgoing = new Map<string, string[]>();

	for (const flow of getChildElements(process, BPMN_NAMESPACE, 'sequenceFlow')) {
		const source = flow.getAttribute('sourceRef');
		const target = flow.getAttribute('targetRef');

		if (source !== null && target !== null) {
			outgoing.set(source, [...(outgoing.get(source) ?? []), target]);
		}
	}

	const startEventIds = Array.from(process.children)
		.filter((child) => child.namespaceURI === BPMN_NAMESPACE && child.localName === 'startEvent')
		.map((startEvent) => startEvent.getAttribute('id'))
		.filter((id) => id !== null);
	const distances = new Map(startEventIds.map((id) => [id, 0]));
	const queue = [...startEventIds];

	for (let current = queue.shift(); current !== undefined; current = queue.shift()) {
		const distance = distances.get(current) ?? 0;

		for (const target of outgoing.get(current) ?? []) {
			if (!distances.has(target)) {
				distances.set(target, distance + 1);
				queue.push(target);
			}
		}
	}

	return distances;
}

function containsCenter(group: Bounds, element: Bounds): boolean {
	const centerX = element.x + element.width / 2;
	const centerY = element.y + element.height / 2;

	return (
		centerX >= group.x && centerX <= group.x + group.width && centerY >= group.y && centerY <= group.y + group.height
	);
}

function getGroupLabel(document: Document, group: Element): string {
	const categoryValueId = group.getAttribute('categoryValueRef');
	const categoryValue = getChildElements(document.documentElement, BPMN_NAMESPACE, 'categoryValue').find(
		(candidate) => candidate.getAttribute('id') === categoryValueId,
	);

	return categoryValue?.getAttribute('value') || group.getAttribute('name') || group.getAttribute('id') || '';
}

function parseCaseModel(xml: string, processDefinitionId: string): CaseModel {
	const document = new DOMParser().parseFromString(xml, 'application/xml');

	if (document.getElementsByTagName('parsererror').length > 0) {
		return EMPTY_CASE_MODEL;
	}

	const elementNames = new Map<string, string>();

	for (const element of getChildElements(document.documentElement, BPMN_NAMESPACE, '*')) {
		const id = element.getAttribute('id');
		const name = element.getAttribute('name');

		if (id !== null && name !== null && FLOW_NODE_TYPES.has(element.localName)) {
			elementNames.set(id, name);
		}
	}

	const process = getChildElements(document.documentElement, BPMN_NAMESPACE, 'process').find(
		(candidate) => candidate.getAttribute('id') === processDefinitionId,
	);

	if (process === undefined) {
		return {milestones: [], elementNames};
	}

	const shapes = getPlaneShapes(document, processDefinitionId);
	const distances = getFlowDistances(process);
	const groups = getChildElements(process, BPMN_NAMESPACE, 'group').flatMap((group) => {
		const id = group.getAttribute('id');
		const bounds = id === null ? undefined : shapes.get(id);

		return id === null || bounds === undefined ? [] : [{id, label: getGroupLabel(document, group), bounds}];
	});
	const membersByGroup = new Map<string, string[]>(groups.map(({id}) => [id, []]));

	for (const element of getChildElements(process, BPMN_NAMESPACE, '*')) {
		const id = element.getAttribute('id');
		const bounds = id === null ? undefined : shapes.get(id);

		if (id === null || bounds === undefined || !FLOW_NODE_TYPES.has(element.localName)) {
			continue;
		}

		const [smallestGroup] = groups
			.filter((group) => containsCenter(group.bounds, bounds))
			.sort((first, second) => first.bounds.width * first.bounds.height - second.bounds.width * second.bounds.height);

		if (smallestGroup !== undefined) {
			membersByGroup.get(smallestGroup.id)?.push(id);
		}
	}

	const milestones = groups
		.map((group) => {
			const elementIds = membersByGroup.get(group.id) ?? [];
			const reachableDistances = elementIds.flatMap((id) => {
				const distance = distances.get(id);
				return distance === undefined ? [] : [distance];
			});

			return {
				milestone: {id: group.id, label: group.label, elementIds},
				distance: reachableDistances.length === 0 ? Number.POSITIVE_INFINITY : Math.min(...reachableDistances),
				x: group.bounds.x,
			};
		})
		.filter(({milestone}) => milestone.elementIds.length > 0)
		.sort((first, second) => first.distance - second.distance || first.x - second.x)
		.map(({milestone}) => milestone);

	return {milestones, elementNames};
}

export {parseCaseModel};
export type {Milestone};
