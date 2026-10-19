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

test('should keep modification mode across tabs and ask before leaving the instance', async ({
	network,
	page,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813862220', state: 'ACTIVE'});
	network.use(...processInstanceHeaderHandlers(instance));
	const frameTitle = page.getByText('Process Instance Modification Mode', {exact: true});

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await page.getByRole('button', {name: `Modify Instance ${instance.processInstanceKey}`}).click();
	await page.getByRole('dialog').getByRole('button', {name: 'Continue'}).click();
	await expect(frameTitle).toBeVisible();

	await page.getByRole('link', {name: 'Incidents', exact: true}).click();
	await expect(page).toHaveURL(`/operate/processes/${instance.processInstanceKey}/incidents`);
	await expect(page.getByRole('dialog')).toHaveCount(0);
	await expect(frameTitle).toBeVisible();

	await page.getByRole('link', {name: 'Processes', exact: true}).click();
	const leaveDialog = page.getByRole('dialog', {name: 'Leave Modification Mode'});
	await leaveDialog.getByRole('button', {name: 'Stay'}).click();
	await expect(page).toHaveURL(`/operate/processes/${instance.processInstanceKey}/incidents`);
	await expect(frameTitle).toBeVisible();

	await page.getByRole('link', {name: 'Processes', exact: true}).click();
	await leaveDialog.getByRole('button', {name: 'Leave'}).click();
	await expect(page).toHaveURL(/\/operate\/processes\?/);
});
