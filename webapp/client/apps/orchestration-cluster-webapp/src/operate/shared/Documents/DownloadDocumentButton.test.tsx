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
import {it} from '#/vitest-modules/test-extend';
import {createDocumentReference} from '#/shared-test-modules/api-mocks/documents';
import {toDocumentInfo} from './documentInfo';
import {DownloadDocumentButton} from './DownloadDocumentButton';

describe('<DownloadDocumentButton />', () => {
	it('should download the document with its file name', async () => {
		const screen = await render(
			<DownloadDocumentButton
				document={toDocumentInfo(createDocumentReference({metadata: {fileName: 'invoice.pdf'}}))}
				labelSuffix="invoice"
			/>,
		);

		const link = screen.getByRole('link', {name: 'Download'});
		await expect
			.element(link)
			.toHaveAttribute(
				'href',
				`${window.location.origin}/v2/documents/doc-abc?storeId=in-memory&contentHash=sha256-xyz`,
			);
		await expect.element(link).toHaveAttribute('download', 'invoice.pdf');
	});

	it.for([
		{
			reason: 'expired',
			reference: createDocumentReference({metadata: {expiresAt: '2000-01-01T00:00:00.000Z'}}),
			tooltip: 'Document has expired',
		},
		{reason: 'linkless', reference: createDocumentReference({contentHash: null}), tooltip: 'Download unavailable'},
	])('should disable the download of an $reason document and explain why', async ({reference, tooltip}) => {
		const screen = await render(<DownloadDocumentButton document={toDocumentInfo(reference)} labelSuffix="invoice" />);

		await expect.element(screen.getByRole('button', {name: 'Download'})).toBeDisabled();
		await expect.element(screen.getByRole('link')).not.toBeInTheDocument();
		await userEvent.tab();
		await expect.element(screen.getByRole('tooltip')).toHaveTextContent(tooltip);
	});
});
