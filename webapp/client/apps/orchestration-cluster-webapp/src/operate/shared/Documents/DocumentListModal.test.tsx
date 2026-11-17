/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {describe, expect, vi} from 'vitest';
import {userEvent} from 'vitest/browser';
import {render} from 'vitest-browser-react';
import {it} from '#/vitest-modules/test-extend';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {toDocumentInfo} from './documentInfo';
import {DocumentListModal} from './DocumentListModal';

const LONG_FILE_NAME = `${'a'.repeat(40)}-middle-${'z'.repeat(40)}.pdf`;
const documents = [
	toDocumentInfo(createDocumentReference({documentId: 'doc-1', metadata: {fileName: 'invoice.pdf', size: 1536}})),
	toDocumentInfo(
		createDocumentReference({
			documentId: 'doc-2',
			metadata: {fileName: LONG_FILE_NAME, size: 0, expiresAt: '2000-01-01T00:00:00.000Z'},
		}),
	),
];

describe('<DocumentListModal />', () => {
	it('should list each document with its actions', async () => {
		const onClose = vi.fn();
		const screen = await render(<DocumentListModal documents={documents} labelSuffix="files" onClose={onClose} />);

		const dialog = screen.getByRole('dialog', {name: '2 documents in files'});
		const invoice = dialog.getByRole('listitem', {name: 'invoice.pdf'});
		await expect.element(invoice).toMatchTextContent('1.5 KiB');
		await expect.element(invoice.getByRole('button', {name: 'Preview'})).toBeEnabled();
		await expect.element(invoice.getByRole('link', {name: 'Download'})).toBeVisible();

		const expired = dialog.getByRole('listitem', {name: `${'a'.repeat(29)}…${'z'.repeat(25)}.pdf`});
		await expect.element(expired.getByTitle(LONG_FILE_NAME)).toBeVisible();
		await expect.element(expired.getByText('Expired')).toBeVisible();
		await expect.element(expired.getByRole('button', {name: 'Preview'})).toBeDisabled();
		await expect.element(expired.getByRole('button', {name: 'Download'})).toBeDisabled();

		await userEvent.click(dialog.getByRole('button', {name: 'Close'}));
		expect(onClose).toHaveBeenCalledOnce();
	});

	it.for([
		{hint: 'loadingHint', text: 'Loading more'},
		{hint: 'errorHint', text: 'Loading failed'},
	] as const)('should show a lower-bound count with the $hint', async ({hint, text}) => {
		const screen = await render(
			<DocumentListModal
				documents={documents.slice(0, 1)}
				labelSuffix="files"
				onClose={() => {}}
				{...{[hint]: text}}
			/>,
		);

		const dialog = screen.getByRole('dialog', {name: '1+ document in files'});
		await expect.element(dialog.getByText(text)).toBeVisible();
	});
});
