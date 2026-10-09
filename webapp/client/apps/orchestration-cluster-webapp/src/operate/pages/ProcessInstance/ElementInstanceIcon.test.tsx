/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {render} from 'vitest-browser-react';
import type {BusinessObject, EventType} from 'bpmn-js/lib/NavigatedViewer';
import {http, HttpResponse} from 'msw';
import {it} from '#/vitest-modules/test-extend';
import {ElementInstanceIcon} from './ElementInstanceIcon';

const object = (type: string): BusinessObject => ({
	id: 'element',
	name: '',
	$type: `bpmn:${type}` as BusinessObject['$type'],
});
const cases = [
	'none Process process-root',
	'none Task task-undefined',
	'none ExclusiveGateway gateway-exclusive',
	'none InclusiveGateway gateway-inclusive-or',
	'none ParallelGateway gateway-parallel',
	'none EventBasedGateway gateway-event-based',
	'none ServiceTask task-service',
	'none UserTask task-user',
	'none BusinessRuleTask task-business-rule',
	'none ScriptTask task-script',
	'none ReceiveTask task-receive',
	'none SendTask task-send',
	'none ManualTask task-manual',
	'none CallActivity call-activity',
	'none SubProcess subprocess-embedded',
	'none AdHocSubProcess subprocess-adhoc',
	'none StartEvent event-start',
	'none EndEvent event-end',
	'none IntermediateThrowEvent event-intermediate-none',
	'none IntermediateCatchEvent task-undefined',
	'Error StartEvent event-error-start',
	'Error EndEvent event-error-end',
	'Error BoundaryEvent event-error-boundary',
	'Message StartEvent event-message-start',
	'Message EndEvent event-message-end',
	'Message IntermediateCatchEvent event-message-interrupting',
	'Message IntermediateThrowEvent event-message-throw',
	'Message BoundaryEvent event-message-interrupting',
	'Message BoundaryEvent event-message-non-interrupting non',
	'Timer StartEvent event-timer-start',
	'Timer IntermediateCatchEvent event-timer-interrupting',
	'Timer BoundaryEvent event-timer-interrupting',
	'Timer BoundaryEvent event-timer-non-interrupting non',
	'Terminate EndEvent event-terminate-end',
	'Link IntermediateCatchEvent link-event-intermediate-catch',
	'Link IntermediateThrowEvent link-event-intermediate-throw',
	'Escalation EndEvent escalation-end-event',
	'Escalation StartEvent escalation-start-event',
	'Escalation StartEvent escalation-non-interrupting-start-event non',
	'Escalation IntermediateThrowEvent escalation-intermediate-throw-event',
	'Escalation BoundaryEvent escalation-boundary-event',
	'Escalation BoundaryEvent escalation-boundary-non-interrupting-event non',
	'Signal StartEvent event-signal-start',
	'Signal StartEvent event-signal-non-interrupting-start non',
	'Signal EndEvent event-signal-end',
	'Signal IntermediateCatchEvent event-signal-intermediate-catch',
	'Signal IntermediateThrowEvent event-signal-intermediate-throw',
	'Signal BoundaryEvent event-signal-interrupting-boundary',
	'Signal BoundaryEvent event-signal-non-interrupting-boundary non',
	'Compensate StartEvent compensation-start-event',
	'Compensate EndEvent compensation-end-event',
	'Compensate IntermediateThrowEvent compensation-intermediate-event-throw',
	'Compensate BoundaryEvent compensation-boundary-event',
	'Conditional StartEvent conditional-start-event',
	'Conditional StartEvent conditional-intermediate-catch-non-interrupting-start-event non',
	'Conditional IntermediateCatchEvent conditional-intermediate-catch-event',
	'Conditional BoundaryEvent conditional-intermediate-catch-event',
	'Conditional BoundaryEvent conditional-intermediate-catch-non-interrupting-event non',
];
describe('ElementInstanceIcon', () => {
	it.for(cases)('should preserve the legacy SVG for %s', async (testCase) => {
		const [event, type, expected, non] = testCase.split(' ');
		const businessObject = {
			...object(type!),
			cancelActivity: non ? false : undefined,
			isInterrupting: non ? false : undefined,
			eventDefinitions: event === 'none' ? undefined : [{$type: `bpmn:${event}EventDefinition` as EventType}],
		};
		const screen = await render(<ElementInstanceIcon businessObject={businessObject} />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', expected);
		expect(screen.getByTestId('element-instance-icon').element().querySelector('svg')).not.toBeNull();
	});
	it('should preserve root, event/agent subprocess, and the legacy MI precedence', async () => {
		const businessObject = object('Task');
		businessObject.loopCharacteristics = {$type: 'bpmn:MultiInstanceLoopCharacteristics', isSequential: true};
		const screen = await render(<ElementInstanceIcon businessObject={businessObject} />);
		await expect
			.element(screen.getByTestId('element-instance-icon'))
			.toHaveAttribute('data-icon', 'multi-instance-parallel');
		businessObject.loopCharacteristics.isSequential = false;
		await screen.rerender(<ElementInstanceIcon businessObject={businessObject} />);
		await expect
			.element(screen.getByTestId('element-instance-icon'))
			.toHaveAttribute('data-icon', 'multi-instance-sequential');
		await screen.rerender(<ElementInstanceIcon businessObject={{...object('SubProcess'), triggeredByEvent: true}} />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', 'subprocess-event');
		await screen.rerender(
			<ElementInstanceIcon
				businessObject={{
					...object('AdHocSubProcess'),
					extensionElements: {
						values: [{$type: 'zeebe:taskDefinition', type: 'io.camunda.agenticai:aiagent-job-worker'}],
					},
				}}
			/>,
		);
		await expect
			.element(screen.getByTestId('element-instance-icon'))
			.toHaveAttribute('data-icon', 'subprocess-adhoc-inner-instance');
		await screen.rerender(<ElementInstanceIcon businessObject={businessObject} root />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', 'process-root');
	});
	it('should validate template URLs, limit templates to activities/events, and fall back on image failure', async ({
		worker,
	}) => {
		worker.use(http.get('/broken-template.svg', () => new HttpResponse(null, {status: 404})));
		const businessObject = {...object('ServiceTask'), $instanceOf: () => true, get: () => 'javascript:alert(1)'};
		const screen = await render(<ElementInstanceIcon businessObject={businessObject} />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', 'task-service');
		businessObject.get = () => 'data:image/svg+xml,%3Csvg xmlns="http://www.w3.org/2000/svg"/%3E';
		await screen.rerender(<ElementInstanceIcon businessObject={businessObject} />);
		expect(screen.getByTestId('element-instance-icon').element().querySelector('img')?.src).toMatch(/^data:image\//);
		for (const scheme of ['http:', 'https:']) {
			businessObject.get = () => `${scheme}//${location.host}/broken-template.svg`;
			await screen.rerender(<ElementInstanceIcon businessObject={businessObject} />);
			expect(screen.getByTestId('element-instance-icon').element().querySelector('img')?.src).toMatch(/^https?:/);
		}
		businessObject.get = () => new URL('/broken-template.svg', location.href).href;
		await screen.rerender(<ElementInstanceIcon businessObject={businessObject} />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', 'task-service');
		businessObject.$instanceOf = () => false;
		businessObject.get = () => 'data:image/svg+xml,%3Csvg/%3E';
		await screen.rerender(<ElementInstanceIcon businessObject={businessObject} />);
		await expect.element(screen.getByTestId('element-instance-icon')).toHaveAttribute('data-icon', 'task-service');
	});
});
