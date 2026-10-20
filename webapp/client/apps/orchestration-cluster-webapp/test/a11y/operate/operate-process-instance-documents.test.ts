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
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {processInstanceHeaderHandlers} from '#/shared-test-modules/process-instance-header-handlers';
import {mockGetDocumentEndpoint, mockQueryVariablesEndpoint} from '#/shared-test-modules/mock-handlers';

const PNG_PIXEL = Buffer.from(
	'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=',
	'base64',
);

test('should have no accessibility violations in document variables and their dialogs', async ({
	network,
	operateProcessInstancePage,
	makeAxeBuilder,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813858013'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createVariable({
							name: 'attachments',
							variableKey: '1',
							value: JSON.stringify([
								createDocumentReference({
									documentId: 'doc-1',
									metadata: {fileName: 'first.png', contentType: 'image/png'},
								}),
								createDocumentReference({
									documentId: 'doc-2',
									metadata: {fileName: 'second.pdf', expiresAt: '2000-01-01T00:00:00.000Z'},
								}),
							]),
						}),
						createVariable({
							name: 'contract',
							variableKey: '2',
							value: JSON.stringify(
								createDocumentReference({documentId: 'doc-3', metadata: {fileName: 'contract.pdf'}}),
							),
						}),
					],
				}),
			),
		}),
		mockGetDocumentEndpoint({
			successResponse: new HttpResponse(PNG_PIXEL, {headers: {'Content-Type': 'image/png'}}),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await expect(operateProcessInstancePage.variableRow('contract').getByRole('link', {name: 'Download'})).toBeVisible();
	expect((await makeAxeBuilder().analyze()).violations).toEqual([]);

	await operateProcessInstancePage.variableRow('attachments').getByRole('button', {name: 'View documents'}).click();
	const list = operateProcessInstancePage.dialog('2 documents in attachments');
	await expect(list.getByRole('listitem', {name: 'second.pdf'})).toBeVisible();
	await expect(list.locator('..')).toHaveCSS('opacity', '1');
	expect(
		(await makeAxeBuilder().include('[role="dialog"][aria-label="2 documents in attachments"]').analyze()).violations,
	).toEqual([]);

	await list.getByRole('listitem', {name: 'first.png'}).getByRole('button', {name: 'Preview'}).click();
	const preview = operateProcessInstancePage.dialog('Preview: first.png');
	await expect(preview.getByRole('img', {name: 'first.png'})).toBeVisible();
	await expect(preview.locator('..')).toHaveCSS('opacity', '1');
	expect(
		(await makeAxeBuilder().include('[role="dialog"][aria-label="Preview: first.png"]').analyze()).violations,
	).toEqual([]);
});
