/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {test, expect} from '#/pw-modules/test-extend';
import {createProcessInstance} from '#/shared-test-modules/api-mocks/process-instances';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';

test('should keep expanded and collapsed header actions and confirmation accessible', async ({
	network,
	page,
	operateProcessInstancePage,
	makeAxeBuilder,
}) => {
	const instance = createProcessInstance({
		processInstanceKey: '2251799813692001',
		tenantId: 'tenant-a',
		hasIncident: true,
	});
	network.use(...processInstanceHeaderHandlers(instance));
	await operateProcessInstancePage.goto(instance.processInstanceKey);
	await expect(operateProcessInstancePage.action('Retry', instance.processInstanceKey)).toBeEnabled();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.setViewportSize({width: 1024, height: 768});
	await operateProcessInstancePage.actionsMenu.click();
	await expect(page.getByRole('menuitem', {name: 'Suspend'})).toBeEnabled();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
	await page.getByRole('menuitem', {name: 'Cancel'}).click();
	await expect(operateProcessInstancePage.apply).toBeEnabled();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);
});
