/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import styled from 'styled-components';

const assets = import.meta.glob<string>('./element-icons/*.svg', {eager: true, query: '?raw', import: 'default'});
const shapes: Record<string, string> = {
	Process: 'process-root',
	SubProcess: 'subprocess-embedded',
	CallActivity: 'call-activity',
	ExclusiveGateway: 'gateway-exclusive',
	InclusiveGateway: 'gateway-inclusive-or',
	ParallelGateway: 'gateway-parallel',
	EventBasedGateway: 'gateway-event-based',
	ServiceTask: 'task-service',
	UserTask: 'task-user',
	BusinessRuleTask: 'task-business-rule',
	ScriptTask: 'task-script',
	ReceiveTask: 'task-receive',
	SendTask: 'task-send',
	ManualTask: 'task-manual',
	StartEvent: 'event-start',
	EndEvent: 'event-end',
	IntermediateThrowEvent: 'event-intermediate-none',
};
const events: Record<string, Partial<Record<string, string>> & {default: string}> = {
	Error: {default: 'event-error-start', EndEvent: 'event-error-end', BoundaryEvent: 'event-error-boundary'},
	Message: {
		default: 'event-message-start',
		EndEvent: 'event-message-end',
		IntermediateCatchEvent: 'event-message-interrupting',
		IntermediateThrowEvent: 'event-message-throw',
		BoundaryEvent: 'event-message-interrupting',
		nonBoundary: 'event-message-non-interrupting',
	},
	Timer: {
		default: 'event-timer-start',
		IntermediateCatchEvent: 'event-timer-interrupting',
		BoundaryEvent: 'event-timer-interrupting',
		nonBoundary: 'event-timer-non-interrupting',
	},
	Terminate: {default: 'event-terminate-end'},
	Link: {default: 'link-event-intermediate-catch', IntermediateThrowEvent: 'link-event-intermediate-throw'},
	Escalation: {
		default: 'escalation-end-event',
		StartEvent: 'escalation-start-event',
		nonStart: 'escalation-non-interrupting-start-event',
		IntermediateThrowEvent: 'escalation-intermediate-throw-event',
		BoundaryEvent: 'escalation-boundary-event',
		nonBoundary: 'escalation-boundary-non-interrupting-event',
	},
	Signal: {
		default: 'event-signal-start',
		nonStart: 'event-signal-non-interrupting-start',
		EndEvent: 'event-signal-end',
		IntermediateCatchEvent: 'event-signal-intermediate-catch',
		IntermediateThrowEvent: 'event-signal-intermediate-throw',
		BoundaryEvent: 'event-signal-interrupting-boundary',
		nonBoundary: 'event-signal-non-interrupting-boundary',
	},
	Compensate: {
		default: 'compensation-start-event',
		EndEvent: 'compensation-end-event',
		IntermediateThrowEvent: 'compensation-intermediate-event-throw',
		BoundaryEvent: 'compensation-boundary-event',
	},
	Conditional: {
		default: 'conditional-start-event',
		nonStart: 'conditional-intermediate-catch-non-interrupting-start-event',
		IntermediateCatchEvent: 'conditional-intermediate-catch-event',
		BoundaryEvent: 'conditional-intermediate-catch-event',
		nonBoundary: 'conditional-intermediate-catch-non-interrupting-event',
	},
};

function resolveElementIcon(object?: BusinessObject, root = false) {
	if (root) {
		return 'process-root';
	}
	if (!object) {
		return 'task-undefined';
	}
	const type = object.$type.slice(5);
	if (type === 'AdHocSubProcess') {
		return object.extensionElements?.values.some(
			(value) =>
				value.$type === 'zeebe:taskDefinition' && value.type?.startsWith('io.camunda.agenticai:aiagent-job-worker'),
		)
			? 'subprocess-adhoc-inner-instance'
			: 'subprocess-adhoc';
	}
	if (type === 'SubProcess' && object.triggeredByEvent === true) {
		return 'subprocess-event';
	}
	if (shapes[type] && !type.endsWith('Event')) {
		return shapes[type]!;
	}
	if (object.loopCharacteristics?.$type === 'bpmn:MultiInstanceLoopCharacteristics') {
		return object.loopCharacteristics.isSequential ? 'multi-instance-parallel' : 'multi-instance-sequential';
	}
	const event = events[object.eventDefinitions?.[0]?.$type.replace(/^bpmn:|EventDefinition$/g, '') ?? ''];
	const variant =
		type === 'BoundaryEvent' && object.cancelActivity === false
			? 'nonBoundary'
			: type === 'StartEvent' && object.isInterrupting === false
				? 'nonStart'
				: type;
	return event ? (event[variant] ?? event[type] ?? event.default) : (shapes[type] ?? 'task-undefined');
}

const Frame = styled.span`
	display: inline-flex;
	flex-shrink: 0;
	svg {
		width: 26px;
		height: 26px;
		fill: currentColor;
	}
	img {
		width: 26px;
		height: 26px;
		box-sizing: border-box;
		padding: 4px;
		object-fit: contain;
	}
	&[data-icon^='gateway-'] {
		position: relative;
		top: 3px;
		right: 2px;
	}
`;

function ElementInstanceIcon({businessObject, root = false}: {businessObject?: BusinessObject; root?: boolean}) {
	const [failedIcon, setFailedIcon] = useState<string>();
	const source = businessObject?.get?.('zeebe:modelerTemplateIcon') ?? businessObject?.['zeebe:modelerTemplateIcon'];
	const template =
		!root &&
		(businessObject?.$instanceOf?.('bpmn:Activity') || businessObject?.$instanceOf?.('bpmn:Event')) &&
		typeof source === 'string' &&
		/^(https?:\/\/|data:image\/)/i.test(source) &&
		source !== failedIcon
			? source
			: null;
	const name = resolveElementIcon(businessObject, root);
	return template ? (
		<Frame data-testid="element-instance-icon">
			<img key={template} src={template} alt="" aria-hidden="true" onError={() => setFailedIcon(template)} />
		</Frame>
	) : (
		<Frame
			data-testid="element-instance-icon"
			aria-hidden="true"
			data-icon={name}
			dangerouslySetInnerHTML={{__html: assets[`./element-icons/element-${name}.svg`]!}}
		/>
	);
}

export {ElementInstanceIcon};
