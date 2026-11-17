/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, it} from 'vitest';
import type {BusinessObject} from 'bpmn-js/lib/NavigatedViewer';
import {getTemplateIconSource, resolveElementIcon} from './elementIcons';

function createBusinessObject(properties: Partial<BusinessObject> & {$type: string}) {
	return {id: 'element', name: 'Element', ...properties} as BusinessObject;
}

describe('resolveElementIcon', () => {
	it('should resolve the process icon for the root', () => {
		expect(resolveElementIcon(undefined, true)).toBe('process-root');
	});

	it('should resolve the generic task icon when the element is unknown', () => {
		expect(resolveElementIcon(undefined)).toBe('task-undefined');
	});

	it('should resolve the icon of the element type', () => {
		expect(resolveElementIcon(createBusinessObject({$type: 'bpmn:UserTask'}))).toBe('task-user');
	});

	it('should resolve the event subprocess icon when the subprocess is triggered by an event', () => {
		const businessObject = createBusinessObject({$type: 'bpmn:SubProcess', triggeredByEvent: true});

		expect(resolveElementIcon(businessObject)).toBe('subprocess-event');
	});

	it('should resolve the non-interrupting icon of a boundary event that does not cancel its activity', () => {
		const businessObject = createBusinessObject({
			$type: 'bpmn:BoundaryEvent',
			cancelActivity: false,
			eventDefinitions: [{$type: 'bpmn:TimerEventDefinition'}],
		} as Partial<BusinessObject> & {$type: string});

		expect(resolveElementIcon(businessObject)).toBe('event-timer-non-interrupting');
	});
});

describe('getTemplateIconSource', () => {
	const templateIcon = 'data:image/svg+xml;base64,PHN2Zy8+';
	const task = createBusinessObject({
		$type: 'bpmn:ServiceTask',
		$instanceOf: (type: string) => type === 'bpmn:Activity',
		get: () => templateIcon,
	} as Partial<BusinessObject> & {$type: string});

	it('should return the template icon of an activity', () => {
		expect(getTemplateIconSource(task, false)).toBe(templateIcon);
	});

	it('should ignore the template icon of the root', () => {
		expect(getTemplateIconSource(task, true)).toBeNull();
	});

	it('should ignore a template icon that failed to load', () => {
		expect(getTemplateIconSource(task, false, templateIcon)).toBeNull();
	});
});
