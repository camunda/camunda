/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {it} from '#/vitest-modules/test-extend';
import {parseDiagramXML} from '#/operate/shared/utils/bpmn';
import {BPMN_XML, MIGRATION_SOURCE_BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {
	getAutoMapping,
	getMigrationElements,
	getTargetChoices,
	hasEmbeddedForm,
	isCamundaUserTask,
} from './migrationMapping';

function definitions(process: string) {
	return `<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0">${process}</bpmn:definitions>`;
}

async function getElements(process: string, processId = 'process') {
	return getMigrationElements(await parseDiagramXML(definitions(process)), processId);
}

function ids(elements: {id: string}[]) {
	return elements.map(({id}) => id);
}

describe('migrationMapping', () => {
	it('should list migratable elements of the process and merging sequence flows', async () => {
		const elements = await getElements(`
			<bpmn:process id="process">
				<bpmn:startEvent id="start" />
				<bpmn:serviceTask id="task" />
				<bpmn:boundaryEvent id="timerBoundary" attachedToRef="task"><bpmn:timerEventDefinition /></bpmn:boundaryEvent>
				<bpmn:boundaryEvent id="errorBoundary" attachedToRef="task"><bpmn:errorEventDefinition /></bpmn:boundaryEvent>
				<bpmn:intermediateCatchEvent id="messageCatch"><bpmn:messageEventDefinition /></bpmn:intermediateCatchEvent>
				<bpmn:subProcess id="eventSubProcess" triggeredByEvent="true">
					<bpmn:startEvent id="eventStart"><bpmn:escalationEventDefinition /></bpmn:startEvent>
				</bpmn:subProcess>
				<bpmn:parallelGateway id="merge"><bpmn:incoming>flowA</bpmn:incoming><bpmn:incoming>flowB</bpmn:incoming></bpmn:parallelGateway>
				<bpmn:sequenceFlow id="flowA" sourceRef="task" targetRef="merge" />
				<bpmn:sequenceFlow id="flowB" sourceRef="messageCatch" targetRef="merge" />
				<bpmn:endEvent id="end" />
			</bpmn:process>
			<bpmn:process id="otherProcess">
				<bpmn:userTask id="otherTask" />
			</bpmn:process>
		`);

		expect(ids(elements.elements)).toEqual([
			'task',
			'timerBoundary',
			'messageCatch',
			'eventSubProcess',
			'eventStart',
			'merge',
		]);
		expect(ids(elements.sequenceFlows)).toEqual(['flowA', 'flowB']);
	});

	it('should not list merging sequence flows of other processes in the same diagram', async () => {
		const elements = await getElements(`
			<bpmn:process id="process">
				<bpmn:serviceTask id="task" />
			</bpmn:process>
			<bpmn:process id="otherProcess">
				<bpmn:serviceTask id="otherTaskA" />
				<bpmn:serviceTask id="otherTaskB" />
				<bpmn:parallelGateway id="otherMerge"><bpmn:incoming>otherFlowA</bpmn:incoming><bpmn:incoming>otherFlowB</bpmn:incoming></bpmn:parallelGateway>
				<bpmn:sequenceFlow id="otherFlowA" sourceRef="otherTaskA" targetRef="otherMerge" />
				<bpmn:sequenceFlow id="otherFlowB" sourceRef="otherTaskB" targetRef="otherMerge" />
			</bpmn:process>
		`);

		expect(ids(elements.elements)).toEqual(['task']);
		expect(ids(elements.sequenceFlows)).toEqual([]);
	});

	it.for([
		{
			description: 'boundary events with the same event type',
			source: '<bpmn:boundaryEvent id="source" attachedToRef="task"><bpmn:timerEventDefinition /></bpmn:boundaryEvent>',
			target: '<bpmn:boundaryEvent id="target" attachedToRef="task"><bpmn:timerEventDefinition /></bpmn:boundaryEvent>',
			isMappable: true,
		},
		{
			description: 'boundary events with different event types',
			source: '<bpmn:boundaryEvent id="source" attachedToRef="task"><bpmn:timerEventDefinition /></bpmn:boundaryEvent>',
			target:
				'<bpmn:boundaryEvent id="target" attachedToRef="task"><bpmn:messageEventDefinition /></bpmn:boundaryEvent>',
			isMappable: false,
		},
		{
			description: 'event sub processes with the same start event type',
			source:
				'<bpmn:subProcess id="source" triggeredByEvent="true"><bpmn:startEvent id="s"><bpmn:messageEventDefinition /></bpmn:startEvent></bpmn:subProcess>',
			target:
				'<bpmn:subProcess id="target" triggeredByEvent="true"><bpmn:startEvent id="t"><bpmn:messageEventDefinition /></bpmn:startEvent></bpmn:subProcess>',
			isMappable: true,
		},
		{
			description: 'an embedded sub process and an event sub process',
			source: '<bpmn:subProcess id="source" />',
			target:
				'<bpmn:subProcess id="target" triggeredByEvent="true"><bpmn:startEvent id="t"><bpmn:timerEventDefinition /></bpmn:startEvent></bpmn:subProcess>',
			isMappable: false,
		},
		{
			description: 'parallel multi instance tasks',
			source: '<bpmn:serviceTask id="source"><bpmn:multiInstanceLoopCharacteristics /></bpmn:serviceTask>',
			target:
				'<bpmn:serviceTask id="target"><bpmn:multiInstanceLoopCharacteristics isSequential="false" /></bpmn:serviceTask>',
			isMappable: true,
		},
		{
			description: 'a sequential and a parallel multi instance task',
			source:
				'<bpmn:serviceTask id="source"><bpmn:multiInstanceLoopCharacteristics isSequential="true" /></bpmn:serviceTask>',
			target: '<bpmn:serviceTask id="target"><bpmn:multiInstanceLoopCharacteristics /></bpmn:serviceTask>',
			isMappable: false,
		},
		{
			description: 'a multi instance task and a plain task',
			source: '<bpmn:serviceTask id="source"><bpmn:multiInstanceLoopCharacteristics /></bpmn:serviceTask>',
			target: '<bpmn:serviceTask id="target" />',
			isMappable: false,
		},
		{
			description: 'tasks of different types',
			source: '<bpmn:serviceTask id="source" />',
			target: '<bpmn:userTask id="target" />',
			isMappable: false,
		},
	])('should decide whether $description can be mapped', async ({source, target, isMappable}) => {
		const sourceElements = await getElements(
			`<bpmn:process id="process"><bpmn:task id="task" />${source}</bpmn:process>`,
		);
		const targetElements = await getElements(
			`<bpmn:process id="process"><bpmn:task id="task" />${target}</bpmn:process>`,
		);
		const sourceElement = sourceElements.elements.find(({id}) => id === 'source')!;

		expect(ids(getTargetChoices(sourceElement, targetElements))).toEqual(isMappable ? ['target'] : []);
	});

	it('should offer every mappable target sequence flow for a source sequence flow', async () => {
		const process = ([first, second]: [string, string]) => `
			<bpmn:process id="process">
				<bpmn:serviceTask id="a" />
				<bpmn:serviceTask id="b" />
				<bpmn:inclusiveGateway id="merge">
					<bpmn:incoming>${first}</bpmn:incoming>
					<bpmn:incoming>${second}</bpmn:incoming>
				</bpmn:inclusiveGateway>
				<bpmn:sequenceFlow id="${first}" sourceRef="a" targetRef="merge" />
				<bpmn:sequenceFlow id="${second}" sourceRef="b" targetRef="merge" />
			</bpmn:process>
		`;
		const source = await getElements(process(['flowA', 'flowB']));
		const target = await getElements(process(['flowC', 'flowD']));

		expect(ids(getTargetChoices(source.sequenceFlows[0]!, target))).toEqual(['flowC', 'flowD']);
	});

	it('should auto-map only elements whose id exists among their target choices', async () => {
		const source = await getElements(`
			<bpmn:process id="process">
				<bpmn:serviceTask id="task" />
				<bpmn:userTask id="review" />
				<bpmn:boundaryEvent id="boundary" attachedToRef="task"><bpmn:timerEventDefinition /></bpmn:boundaryEvent>
			</bpmn:process>
		`);
		const target = await getElements(`
			<bpmn:process id="process">
				<bpmn:serviceTask id="task" />
				<bpmn:serviceTask id="review" />
				<bpmn:boundaryEvent id="boundary" attachedToRef="task"><bpmn:messageEventDefinition /></bpmn:boundaryEvent>
			</bpmn:process>
		`);

		expect(getAutoMapping(source, target)).toEqual({task: 'task'});
	});

	it('should detect a job worker user task with an embedded form and a Camunda user task', async () => {
		const source = getMigrationElements(await parseDiagramXML(MIGRATION_SOURCE_BPMN_XML), 'my_simple_process');
		const target = getMigrationElements(await parseDiagramXML(BPMN_XML), 'my_simple_process');
		const sourceTask = source.elements.find(({id}) => id === 'task-1');
		const targetTask = target.elements.find(({id}) => id === 'task-1');

		expect(hasEmbeddedForm(sourceTask)).toBe(true);
		expect(isCamundaUserTask(sourceTask)).toBe(false);
		expect(hasEmbeddedForm(targetTask)).toBe(false);
		expect(isCamundaUserTask(targetTask)).toBe(true);
	});
});
