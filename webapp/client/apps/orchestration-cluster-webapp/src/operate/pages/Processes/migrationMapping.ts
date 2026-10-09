/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import type {BusinessObject, ElementType, EventType} from 'bpmn-js/lib/NavigatedViewer';
import type {DiagramModel} from 'bpmn-moddle';
import {getFlowNodes, hasType} from '#/operate/shared/utils/elements';

type MigrationElements = {elements: BusinessObject[]; sequenceFlows: BusinessObject[]};

const MIGRATABLE_TYPES: ElementType[] = [
	'bpmn:ServiceTask',
	'bpmn:UserTask',
	'bpmn:SubProcess',
	'bpmn:AdHocSubProcess',
	'bpmn:CallActivity',
	'bpmn:ReceiveTask',
	'bpmn:BusinessRuleTask',
	'bpmn:ScriptTask',
	'bpmn:SendTask',
];

function getEventType(businessObject: BusinessObject) {
	return businessObject.eventDefinitions?.[0]?.$type;
}

function hasEventType(businessObject: BusinessObject, types: EventType[]) {
	const eventType = getEventType(businessObject);
	return eventType !== undefined && types.includes(eventType);
}

function isStartEvent(businessObject: BusinessObject) {
	return hasType({businessObject, types: ['bpmn:StartEvent']});
}

function isEventSubProcess(businessObject: BusinessObject, eventTypes?: EventType[]) {
	if (!hasType({businessObject, types: ['bpmn:SubProcess']}) || businessObject.triggeredByEvent !== true) {
		return false;
	}
	if (eventTypes === undefined) {
		return true;
	}
	return (
		businessObject.flowElements?.some((element) => isStartEvent(element) && hasEventType(element, eventTypes)) ?? false
	);
}

function getEventSubProcessType(businessObject: BusinessObject) {
	if (!isEventSubProcess(businessObject)) {
		return undefined;
	}
	return businessObject.flowElements?.find(isStartEvent)?.eventDefinitions?.[0]?.$type;
}

function isMultiInstance(businessObject: BusinessObject) {
	return businessObject.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics';
}

function getMultiInstanceType(businessObject: BusinessObject) {
	if (businessObject.loopCharacteristics === undefined) {
		return undefined;
	}
	return businessObject.loopCharacteristics.isSequential ? 'sequential' : 'parallel';
}

function isMigratableElement(businessObject: BusinessObject) {
	if (
		hasType({businessObject, types: ['bpmn:BoundaryEvent']}) &&
		hasEventType(businessObject, [
			'bpmn:MessageEventDefinition',
			'bpmn:TimerEventDefinition',
			'bpmn:SignalEventDefinition',
			'bpmn:CompensateEventDefinition',
			'bpmn:ConditionalEventDefinition',
		])
	) {
		return true;
	}
	if (
		hasType({businessObject, types: ['bpmn:IntermediateCatchEvent']}) &&
		hasEventType(businessObject, [
			'bpmn:MessageEventDefinition',
			'bpmn:TimerEventDefinition',
			'bpmn:SignalEventDefinition',
			'bpmn:ConditionalEventDefinition',
		])
	) {
		return true;
	}
	if (isEventSubProcess(businessObject)) {
		return isEventSubProcess(businessObject, [
			'bpmn:MessageEventDefinition',
			'bpmn:TimerEventDefinition',
			'bpmn:SignalEventDefinition',
			'bpmn:ErrorEventDefinition',
			'bpmn:EscalationEventDefinition',
			'bpmn:ConditionalEventDefinition',
		]);
	}
	if (
		hasType({
			businessObject,
			types: ['bpmn:ExclusiveGateway', 'bpmn:EventBasedGateway', 'bpmn:InclusiveGateway', 'bpmn:ParallelGateway'],
		})
	) {
		return true;
	}
	if (
		isStartEvent(businessObject) &&
		hasEventType(businessObject, [
			'bpmn:TimerEventDefinition',
			'bpmn:SignalEventDefinition',
			'bpmn:ErrorEventDefinition',
			'bpmn:EscalationEventDefinition',
			'bpmn:MessageEventDefinition',
			'bpmn:ConditionalEventDefinition',
		]) &&
		businessObject.$parent !== undefined &&
		isEventSubProcess(businessObject.$parent)
	) {
		return true;
	}
	return hasType({businessObject, types: MIGRATABLE_TYPES});
}

function hasParentProcess(businessObject: BusinessObject, processId: string): boolean {
	if (businessObject.$parent === undefined) {
		return false;
	}
	return businessObject.$parent.id === processId || hasParentProcess(businessObject.$parent, processId);
}

function isMappableSequenceFlow({$type, targetRef}: BusinessObject) {
	return (
		$type === 'bpmn:SequenceFlow' &&
		targetRef !== undefined &&
		hasType({businessObject: targetRef, types: ['bpmn:ParallelGateway', 'bpmn:InclusiveGateway']}) &&
		(targetRef.incoming?.length ?? 0) > 1
	);
}

function getMigrationElements(diagramModel: DiagramModel | undefined, processId: string): MigrationElements {
	if (diagramModel === undefined) {
		return {elements: [], sequenceFlows: []};
	}
	return {
		elements: getFlowNodes(diagramModel.elementsById).filter(
			(element) => isMigratableElement(element) && hasParentProcess(element, processId),
		),
		sequenceFlows: Object.values(diagramModel.elementsById).filter(
			(element) => isMappableSequenceFlow(element) && hasParentProcess(element, processId),
		),
	};
}

function isMappableTarget(sourceElement: BusinessObject, targetElement: BusinessObject) {
	if (
		hasType({
			businessObject: sourceElement,
			types: ['bpmn:StartEvent', 'bpmn:IntermediateCatchEvent', 'bpmn:BoundaryEvent'],
		})
	) {
		return sourceElement.$type === targetElement.$type && getEventType(sourceElement) === getEventType(targetElement);
	}
	if (isEventSubProcess(sourceElement)) {
		return getEventSubProcessType(sourceElement) === getEventSubProcessType(targetElement);
	}
	if (isEventSubProcess(targetElement)) {
		return false;
	}
	if (isMultiInstance(sourceElement) || isMultiInstance(targetElement)) {
		return (
			sourceElement.$type === targetElement.$type &&
			getMultiInstanceType(sourceElement) === getMultiInstanceType(targetElement)
		);
	}
	return sourceElement.$type === targetElement.$type;
}

function getTargetChoices(sourceElement: BusinessObject, target: MigrationElements) {
	if (sourceElement.$type === 'bpmn:SequenceFlow') {
		return target.sequenceFlows;
	}
	return target.elements.filter((targetElement) => isMappableTarget(sourceElement, targetElement));
}

function getAutoMapping(source: MigrationElements, target: MigrationElements): Record<string, string> {
	return Object.fromEntries(
		[...source.elements, ...source.sequenceFlows]
			.filter((sourceElement) => getTargetChoices(sourceElement, target).some(({id}) => id === sourceElement.id))
			.map(({id}) => [id, id]),
	);
}

function hasEmbeddedForm(businessObject?: BusinessObject) {
	if (businessObject?.$type !== 'bpmn:UserTask') {
		return false;
	}
	const extensions = businessObject.extensionElements?.values ?? [];
	if (extensions.some(({$type}) => $type === 'zeebe:userTask')) {
		return false;
	}
	const formKey = extensions.find(({$type}) => $type === 'zeebe:formDefinition')?.formKey;
	return formKey !== undefined && formKey.toLowerCase().startsWith('camunda-forms:bpmn:');
}

function isCamundaUserTask(businessObject?: BusinessObject) {
	return (
		businessObject?.$type === 'bpmn:UserTask' &&
		(businessObject.extensionElements?.values?.some(({$type}) => $type === 'zeebe:userTask') ?? false)
	);
}

export {getMigrationElements, getTargetChoices, getAutoMapping, hasEmbeddedForm, isCamundaUserTask};
export type {MigrationElements};
