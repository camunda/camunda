/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {createListenerJob} from '#/shared-test-modules/api-mocks/jobs';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {BPMN_XML} from '#/shared-test-modules/api-mocks/process-definition-xmls';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockGetProcessDefinitionXmlEndpoint, mockQueryJobsEndpoint} from '#/shared-test-modules/mock-handlers';

test('should have no accessibility violations on the listeners and input mappings tabs', async ({
	network,
	page,
	operateProcessInstancePage,
	makeAxeBuilder,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856038'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryJobsEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({items: [createListenerJob({jobKey: '1', state: 'FAILED', type: 'audit-listener'})]}),
			),
		}),
		mockGetProcessDefinitionXmlEndpoint({
			successResponse: HttpResponse.text(
				BPMN_XML.replace(
					'<zeebe:userTask />',
					'<zeebe:userTask /><zeebe:ioMapping><zeebe:input source="=orderId" target="localOrderId" /></zeebe:ioMapping>',
				),
			),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/listeners');
	await expect(page.getByText('audit-listener')).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/input-mappings?elementId=task-1');
	await expect(page.getByText('localOrderId')).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});
