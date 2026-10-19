/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

const BPMN_NAMESPACE = 'http://www.omg.org/spec/BPMN/20100524/MODEL';
const ZEEBE_NAMESPACE = 'http://camunda.org/schema/zeebe/1.0';

// prototype: no design document names the "I am done" activity, so the SDK finds it by this job type, or by its name
const STAGE_DONE_JOB_TYPE = 'case-stage-done';

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

type CaseMarker = {
	/** Name of the variable that holds the case title. */
	titleVariable: string | null;
	/** ID of the form that shows the case details. */
	caseFormId: string | null;
};

type ManualCompletion = {
	/** The activatable "I am done" element, if the stage has one. */
	doneElementId: string | null;
	/** The interrupting message boundary event "Stage done". */
	boundaryEventId: string;
	messageName: string;
	/** The correlation key expression, as written in the BPMN. */
	correlationKey: string | null;
};

type AdHocSettings = {
	requiredElementIds: string[];
	optionalElementIds: string[];
	completionCondition: string | null;
	manualCompletion: ManualCompletion | null;
};

/**
 * - `alternative`: an interrupting event sub-process; it replaces the rest of the line (STAGES.md)
 * - `optional`: a non-interrupting event sub-process; it runs next to the active stage. The design has no
 *   model for optional stages yet (GAP-001), so the line does not show it.
 */
type StageKind = 'subProcess' | 'adHoc' | 'alternative' | 'optional';

type Stage = {
	id: string;
	name: string;
	kind: StageKind;
	/** All flow nodes inside the stage, at any depth. */
	elementIds: string[];
	adHoc: AdHocSettings | null;
	/** The "Stage done" boundary event, also for stages that are not ad-hoc. */
	manualCompletion: ManualCompletion | null;
};

type CaseModel = {
	caseMarker: CaseMarker | null;
	/** Main stages in the order of the sequence flows, then alternative stages. */
	stages: Stage[];
	hasCallActivity: boolean;
	elementNames: Map<string, string>;
	elementTypes: Map<string, string>;
	/** The stage that contains each flow node. */
	stageIdByElementId: Map<string, string>;
};

const EMPTY_CASE_MODEL: CaseModel = {
	caseMarker: null,
	stages: [],
	hasCallActivity: false,
	elementNames: new Map(),
	elementTypes: new Map(),
	stageIdByElementId: new Map(),
};

function isBpmn(element: Element, localName?: string): boolean {
	return element.namespaceURI === BPMN_NAMESPACE && (localName === undefined || element.localName === localName);
}

function isFlowNode(element: Element): boolean {
	return isBpmn(element) && FLOW_NODE_TYPES.has(element.localName);
}

function getExtension(element: Element, localName: string): Element | null {
	const extensionElements = Array.from(element.children).find((child) => isBpmn(child, 'extensionElements'));

	return (
		Array.from(extensionElements?.children ?? []).find(
			(child) => child.namespaceURI === ZEEBE_NAMESPACE && child.localName === localName,
		) ?? null
	);
}

function getChildFlowNodes(element: Element): Element[] {
	return Array.from(element.children).filter(isFlowNode);
}

function getDescendantFlowNodes(element: Element): Element[] {
	return getChildFlowNodes(element).flatMap((child) => [child, ...getDescendantFlowNodes(child)]);
}

function isNonInterrupting(eventSubProcess: Element): boolean {
	return getChildFlowNodes(eventSubProcess).some(
		(child) => isBpmn(child, 'startEvent') && child.getAttribute('isInterrupting') === 'false',
	);
}

function isEventSubProcess(element: Element): boolean {
	return isBpmn(element, 'subProcess') && element.getAttribute('triggeredByEvent') === 'true';
}

function getStringLiterals(expression: string | null): string[] {
	const match = expression === null ? null : /^=\s*\[([\s\S]*)\]\s*$/.exec(expression.trim());

	return match?.[1] === undefined ? [] : Array.from(match[1].matchAll(/"([^"]*)"/g), ([, value]) => value ?? '');
}

function getMessage(
	document: Document,
	messageId: string | null,
): {name: string; correlationKey: string | null} | null {
	const message = Array.from(document.getElementsByTagNameNS(BPMN_NAMESPACE, 'message')).find(
		(candidate) => candidate.getAttribute('id') === messageId,
	);
	const name = message?.getAttribute('name');

	if (message === undefined || name === null || name === undefined) {
		return null;
	}

	return {name, correlationKey: getExtension(message, 'subscription')?.getAttribute('correlationKey') ?? null};
}

function findManualCompletion(document: Document, process: Element, stage: Element): ManualCompletion | null {
	const stageId = stage.getAttribute('id');
	const boundaryEvents = getChildFlowNodes(process).filter(
		(element) =>
			isBpmn(element, 'boundaryEvent') &&
			element.getAttribute('attachedToRef') === stageId &&
			element.getAttribute('cancelActivity') !== 'false',
	);

	for (const boundaryEvent of boundaryEvents) {
		const definition = Array.from(boundaryEvent.children).find((child) => isBpmn(child, 'messageEventDefinition'));
		const message = definition === undefined ? null : getMessage(document, definition.getAttribute('messageRef'));
		const boundaryEventId = boundaryEvent.getAttribute('id');

		if (message !== null && boundaryEventId !== null) {
			return {
				doneElementId: null,
				boundaryEventId,
				messageName: message.name,
				correlationKey: message.correlationKey,
			};
		}
	}

	return null;
}

function findDoneElementId(candidates: Element[]): string | null {
	const sendTasks = candidates.filter((element) => isBpmn(element, 'sendTask'));
	const byJobType = sendTasks.find(
		(element) => getExtension(element, 'taskDefinition')?.getAttribute('type') === STAGE_DONE_JOB_TYPE,
	);
	const byName = sendTasks.find((element) => /done/i.test(element.getAttribute('name') ?? ''));

	return (byJobType ?? byName)?.getAttribute('id') ?? null;
}

function getAdHocSettings(stage: Element, manualCompletion: ManualCompletion | null): AdHocSettings {
	const targets = new Set(
		Array.from(stage.children)
			.filter((child) => isBpmn(child, 'sequenceFlow'))
			.map((flow) => flow.getAttribute('targetRef')),
	);
	const activatable = getChildFlowNodes(stage).filter(
		(element) => !isBpmn(element, 'boundaryEvent') && !targets.has(element.getAttribute('id')),
	);
	const activatableIds = activatable.flatMap((element) => element.getAttribute('id') ?? []);
	const doneElementId = manualCompletion === null ? null : findDoneElementId(activatable);
	const requiredElementIds = getStringLiterals(
		getExtension(stage, 'adHoc')?.getAttribute('activeElementsCollection') ?? null,
	).filter((id) => activatableIds.includes(id));
	const completionCondition = Array.from(stage.children).find((child) => isBpmn(child, 'completionCondition'));

	if (manualCompletion !== null) {
		manualCompletion.doneElementId = doneElementId;
	}

	return {
		requiredElementIds,
		optionalElementIds: activatableIds.filter((id) => id !== doneElementId && !requiredElementIds.includes(id)),
		completionCondition: completionCondition?.textContent?.trim() || null,
		manualCompletion,
	};
}

function getFlowDistances(process: Element): Map<string, number> {
	const outgoing = new Map<string, string[]>();
	const addEdge = (source: string, target: string) => outgoing.set(source, [...(outgoing.get(source) ?? []), target]);

	for (const child of Array.from(process.children)) {
		const source = child.getAttribute('sourceRef');
		const target = child.getAttribute('targetRef');
		const host = child.getAttribute('attachedToRef');
		const id = child.getAttribute('id');

		if (isBpmn(child, 'sequenceFlow') && source !== null && target !== null) {
			addEdge(source, target);
		}

		if (isBpmn(child, 'boundaryEvent') && host !== null && id !== null) {
			addEdge(host, id);
		}
	}

	const startEventIds = getChildFlowNodes(process)
		.filter((element) => isBpmn(element, 'startEvent'))
		.flatMap((element) => element.getAttribute('id') ?? []);
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

function isEventDriven(stage: Stage): boolean {
	return stage.kind === 'alternative' || stage.kind === 'optional';
}

function parseCaseModel(xml: string, processDefinitionId: string): CaseModel {
	const document = new DOMParser().parseFromString(xml, 'application/xml');

	if (document.getElementsByTagName('parsererror').length > 0) {
		return EMPTY_CASE_MODEL;
	}

	const process = Array.from(document.getElementsByTagNameNS(BPMN_NAMESPACE, 'process')).find(
		(candidate) => candidate.getAttribute('id') === processDefinitionId,
	);

	if (process === undefined) {
		return EMPTY_CASE_MODEL;
	}

	const allFlowNodes = getDescendantFlowNodes(process);
	const elementNames = new Map<string, string>();
	const elementTypes = new Map<string, string>();

	for (const element of allFlowNodes) {
		const id = element.getAttribute('id');

		if (id !== null) {
			elementNames.set(id, element.getAttribute('name') || id);
			elementTypes.set(id, element.localName);
		}
	}

	const caseExtension = getExtension(process, 'case');
	const caseMarker: CaseMarker | null =
		caseExtension === null
			? null
			: {
					titleVariable: caseExtension.getAttribute('title') || null,
					caseFormId: caseExtension.getAttribute('caseForm') || null,
				};

	const distances = getFlowDistances(process);
	const stageIdByElementId = new Map<string, string>();
	const stageElements = getChildFlowNodes(process).filter(
		(element) =>
			(isBpmn(element, 'subProcess') || isBpmn(element, 'adHocSubProcess')) && getExtension(element, 'stage') !== null,
	);

	const stages = stageElements.flatMap((element, documentOrder) => {
		const id = element.getAttribute('id');

		if (id === null) {
			return [];
		}

		const elementIds = getDescendantFlowNodes(element).flatMap((child) => child.getAttribute('id') ?? []);
		const manualCompletion = findManualCompletion(document, process, element);
		const isAdHoc = isBpmn(element, 'adHocSubProcess');
		const kind: StageKind = isEventSubProcess(element)
			? isNonInterrupting(element)
				? 'optional'
				: 'alternative'
			: isAdHoc
				? 'adHoc'
				: 'subProcess';

		for (const elementId of elementIds) {
			stageIdByElementId.set(elementId, id);
		}

		const stage: Stage = {
			id,
			name: element.getAttribute('name') || id,
			kind,
			elementIds,
			adHoc: isAdHoc ? getAdHocSettings(element, manualCompletion) : null,
			manualCompletion,
		};

		return [{stage, documentOrder, distance: distances.get(id) ?? Number.POSITIVE_INFINITY}];
	});

	const sortedStages = stages
		.sort(
			(first, second) =>
				Number(isEventDriven(first.stage)) - Number(isEventDriven(second.stage)) ||
				first.distance - second.distance ||
				first.documentOrder - second.documentOrder,
		)
		.map(({stage}) => stage);

	return {
		caseMarker,
		stages: sortedStages,
		hasCallActivity: allFlowNodes.some((element) => isBpmn(element, 'callActivity')),
		elementNames,
		elementTypes,
		stageIdByElementId,
	};
}

export {parseCaseModel, STAGE_DONE_JOB_TYPE};
export type {AdHocSettings, CaseMarker, CaseModel, ManualCompletion, Stage, StageKind};
