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
import {createVariable} from '#/shared-test-modules/api-mocks/variables';
import {createPaginatedResponse} from '#/shared-test-modules/api-mocks/shared';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';

test('should show process instance variables on direct entry and fall back from hidden tabs', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813856033', tenantId: 'tenant-a'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [createVariable({name: 'orderId', value: '"A-1"', processInstanceKey: instance.processInstanceKey})],
				}),
			),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/incidents?tenantId=tenant-a');

	await expect(page).toHaveURL(`/operate/processes/${instance.processInstanceKey}/variables?tenantId=tenant-a`);
	await expect(page.getByRole('link', {name: 'Variables', exact: true})).toHaveAttribute('aria-current', 'page');
	await expect(page.getByRole('link', {name: /Incidents/})).toHaveCount(0);
	await expect(page.getByRole('button', {name: 'Copy orderId'})).toHaveText('"A-1"');
	await expect(page).toHaveTitle(`Operate: Process Instance ${instance.processInstanceKey} of My Process`);
});
