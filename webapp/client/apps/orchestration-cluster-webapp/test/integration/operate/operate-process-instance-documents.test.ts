/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {HttpResponse} from 'msw';
import type {DocumentReference} from '@camunda/camunda-api-zod-schemas/8.11';
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

function documentVariable(name: string, documentId: string, metadata: Partial<DocumentReference['metadata']>) {
	return createVariable({
		name,
		variableKey: documentId,
		value: JSON.stringify(createDocumentReference({documentId, metadata})),
	});
}

test('should preview and download document variables', async ({network, operateProcessInstancePage}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813858009'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						documentVariable('contract', 'doc-pdf', {fileName: 'contract.pdf', contentType: 'application/pdf'}),
						documentVariable('photo', 'doc-png', {fileName: 'photo.png', contentType: 'image/png'}),
					],
				}),
			),
		}),
		mockGetDocumentEndpoint({
			successResponse: new HttpResponse(PNG_PIXEL, {headers: {'Content-Type': 'image/png'}}),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');

	await test.step('download link', async () => {
		const download = operateProcessInstancePage.variableRow('contract').getByRole('link', {name: 'Download'});
		await expect(download).toHaveAttribute(
			'href',
			/\/v2\/documents\/doc-pdf\?storeId=in-memory&contentHash=sha256-xyz$/,
		);
		await expect(download).toHaveAttribute('download', 'contract.pdf');
	});

	await test.step('image preview', async () => {
		await operateProcessInstancePage.variableRow('photo').getByRole('button', {name: 'Preview'}).click();
		const dialog = operateProcessInstancePage.dialog('Preview: photo.png');
		await expect(dialog.getByRole('img', {name: 'photo.png'})).toBeVisible();
		await dialog.getByRole('button', {name: 'Close'}).click();
		await expect(dialog).toBeHidden();
	});
});

test('should preview a PDF document variable', async ({network, operateProcessInstancePage}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813858010'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [documentVariable('contract', 'doc-pdf', {fileName: 'contract.pdf', contentType: 'application/pdf'})],
				}),
			),
		}),
		mockGetDocumentEndpoint({
			successResponse: new HttpResponse('%PDF-1.4\n%%EOF', {headers: {'Content-Type': 'application/pdf'}}),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');
	await operateProcessInstancePage.variableRow('contract').getByRole('button', {name: 'Preview'}).click();

	const dialog = operateProcessInstancePage.dialog('Preview: contract.pdf');
	await expect(dialog.getByTitle('contract.pdf')).toBeVisible();
	await expect(dialog.getByTitle('contract.pdf')).toHaveAttribute('src', /^blob:/);
});

test('should preview a document from a document list and return to the list', async ({
	network,
	operateProcessInstancePage,
}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813858011'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						createVariable({
							name: 'attachments',
							value: JSON.stringify([
								createDocumentReference({
									documentId: 'doc-1',
									metadata: {fileName: 'first.png', contentType: 'image/png'},
								}),
								createDocumentReference({documentId: 'doc-2', metadata: {fileName: 'second.pdf'}}),
							]),
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
	await operateProcessInstancePage.variableRow('attachments').getByRole('button', {name: 'View documents'}).click();

	const list = operateProcessInstancePage.dialog('2 documents in attachments');
	const first = list.getByRole('listitem', {name: 'first.png'});
	await expect(first).toBeVisible();
	await expect(list.getByRole('listitem', {name: 'second.pdf'})).toBeVisible();

	await first.getByRole('button', {name: 'Preview'}).click();
	const preview = operateProcessInstancePage.dialog('Preview: first.png');
	await expect(preview.getByRole('img', {name: 'first.png'})).toBeVisible();
	await preview.getByRole('button', {name: 'Close'}).click();

	await expect(preview).toBeHidden();
	await expect(list).toBeVisible();
	await expect(first).toBeVisible();
});

test('should explain why document actions are disabled', async ({network, page, operateProcessInstancePage}) => {
	const instance = createProcessInstance({processInstanceKey: '2251799813858012'});
	network.use(...processInstanceHeaderHandlers(instance));
	network.use(
		mockQueryVariablesEndpoint({
			successResponse: HttpResponse.json(
				createPaginatedResponse({
					items: [
						documentVariable('expired', 'doc-expired', {expiresAt: '2000-01-01T00:00:00.000Z'}),
						documentVariable('notes', 'doc-txt', {fileName: 'notes.txt', contentType: 'text/plain'}),
					],
				}),
			),
		}),
	);

	await operateProcessInstancePage.goto(instance.processInstanceKey, '/variables');

	const expired = operateProcessInstancePage.variableRow('expired');
	await expect(expired.getByText('Expired', {exact: true})).toBeVisible();
	await expect(expired.getByRole('button', {name: 'Preview'})).toBeDisabled();
	await expect(expired.getByRole('button', {name: 'Download'})).toBeDisabled();
	await expired.getByRole('button', {name: 'Download'}).hover({force: true});
	await expect(page.getByRole('tooltip', {name: 'Document has expired'})).toBeVisible();

	const unsupported = operateProcessInstancePage.variableRow('notes');
	await expect(unsupported.getByRole('button', {name: 'Preview'})).toBeDisabled();
	await expect(unsupported.getByRole('link', {name: 'Download'})).toBeVisible();
	await unsupported.getByRole('button', {name: 'Preview'}).hover({force: true});
	await expect(page.getByRole('tooltip', {name: 'Unsupported file type'})).toBeVisible();
});
