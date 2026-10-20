/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {HttpResponse} from 'msw';
import {QueryClient, QueryClientProvider} from '@tanstack/react-query';
import {it} from '#/vitest-modules/test-extend';
import {mockGetDocumentEndpoint} from '#/shared-test-modules/mock-handlers';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {toDocumentInfo, type DocumentInfo} from './documentInfo';
import {PreviewDocumentButton} from './PreviewDocumentButton';

const PNG_PIXEL = Uint8Array.from(
	atob('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII='),
	(char) => char.charCodeAt(0),
);

function createDocumentInfo(overrides: Parameters<typeof createDocumentReference>[0]) {
	return toDocumentInfo(createDocumentReference({documentId: crypto.randomUUID(), ...overrides}));
}

function renderButton(documentInfo: DocumentInfo) {
	return render(
		<QueryClientProvider client={new QueryClient({defaultOptions: {queries: {retry: false}}})}>
			<PreviewDocumentButton document={documentInfo} labelSuffix="invoice" />
		</QueryClientProvider>,
	);
}

describe('<PreviewDocumentButton />', () => {
	it('should preview an image document', async ({worker}) => {
		worker.use(mockGetDocumentEndpoint({successResponse: HttpResponse.arrayBuffer(PNG_PIXEL.buffer)}));
		const screen = await renderButton(
			createDocumentInfo({metadata: {fileName: 'photo.png', contentType: 'image/png'}}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Preview'}));

		const dialog = screen.getByRole('dialog', {name: 'Preview: photo.png'});
		await expect.element(dialog.getByRole('img', {name: 'photo.png'})).toBeVisible();
		await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
		await expect.element(dialog).not.toBeInTheDocument();
	});

	it('should show an error when the image preview fails to load', async ({worker}) => {
		worker.use(mockGetDocumentEndpoint({successResponse: new HttpResponse(null, {status: 404})}));
		const screen = await renderButton(
			createDocumentInfo({metadata: {fileName: 'photo.png', contentType: 'image/png'}}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Preview'}));

		await expect
			.element(screen.getByRole('dialog').getByText('Failed to load image preview for "photo.png".'))
			.toBeVisible();
	});

	it('should preview a PDF document', async ({worker}) => {
		worker.use(
			mockGetDocumentEndpoint({
				successResponse: new HttpResponse('%PDF-1.4', {headers: {'Content-Type': 'application/pdf'}}),
			}),
		);
		const screen = await renderButton(
			createDocumentInfo({metadata: {fileName: 'contract.pdf', contentType: 'application/pdf'}}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Preview'}));

		await expect
			.element(screen.getByRole('dialog', {name: 'Preview: contract.pdf'}).getByTitle('contract.pdf'))
			.toBeVisible();
	});

	it('should preview a JSON document', async ({worker}) => {
		worker.use(mockGetDocumentEndpoint({successResponse: HttpResponse.json({amount: 100})}));
		const screen = await renderButton(
			createDocumentInfo({metadata: {fileName: 'data.json', contentType: 'application/json'}}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Preview'}));

		const dialog = screen.getByRole('dialog', {name: 'Preview: data.json'});
		await expect.element(dialog.getByRole('textbox', {name: 'Value', exact: true})).toBeVisible();
		await expect.element(dialog.getByText('"amount"')).toBeVisible();
	});

	it('should show an error when the JSON preview fails to load', async ({worker}) => {
		worker.use(mockGetDocumentEndpoint({successResponse: new HttpResponse(null, {status: 500})}));
		const screen = await renderButton(
			createDocumentInfo({metadata: {fileName: 'data.json', contentType: 'application/json'}}),
		);

		await userEvent.click(screen.getByRole('button', {name: 'Preview'}));

		const dialog = screen.getByRole('dialog', {name: 'Preview: data.json'});
		await expect.element(dialog.getByText('Failed to load JSON preview for "data.json".')).toBeVisible();
		await expect.element(dialog.getByTestId('json-document-preview-spinner')).not.toBeInTheDocument();
	});

	it.for([
		{
			reason: 'expired',
			documentInfo: createDocumentInfo({metadata: {contentType: 'image/png', expiresAt: '2000-01-01T00:00:00.000Z'}}),
			tooltip: 'Document has expired',
		},
		{
			reason: 'without content hash',
			documentInfo: createDocumentInfo({contentHash: null, metadata: {contentType: 'image/png'}}),
			tooltip: 'Preview unavailable',
		},
		{
			reason: 'unsupported',
			documentInfo: createDocumentInfo({metadata: {contentType: 'text/plain'}}),
			tooltip: 'Unsupported file type',
		},
	])('should disable the preview of an $reason document and explain why', async ({documentInfo, tooltip}) => {
		const screen = await renderButton(documentInfo);
		const button = screen.getByRole('button', {name: 'Preview'});

		await expect.element(button).toBeDisabled();
		await userEvent.tab();
		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(tooltip);
	});
});
