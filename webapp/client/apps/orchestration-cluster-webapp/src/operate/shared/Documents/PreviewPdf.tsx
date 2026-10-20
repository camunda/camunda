/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {InlineLoading, InlineNotification} from '@carbon/react';
import {PreviewPdfFrame} from './styled';

type Props = {
	src: string;
	fileName: string;
};

type PdfState = {status: 'loading'} | {status: 'error'} | {status: 'loaded'; url: string};

const PreviewPdf: React.FC<Props> = ({src, fileName}) => {
	const {t} = useTranslation();
	const [state, setState] = useState<PdfState>({status: 'loading'});

	useEffect(() => {
		const controller = new AbortController();
		let url: string | null = null;

		fetch(src, {credentials: 'include', signal: controller.signal})
			.then(async (response) => {
				if (!response.ok) {
					throw new Error(`Failed to load document (status ${response.status})`);
				}
				url = URL.createObjectURL(await response.blob());
				setState({status: 'loaded', url});
			})
			.catch(() => {
				if (!controller.signal.aborted) {
					setState({status: 'error'});
				}
			});

		return () => {
			controller.abort();
			if (url !== null) {
				URL.revokeObjectURL(url);
			}
		};
	}, [src]);

	if (state.status === 'error') {
		return (
			<InlineNotification
				kind="error"
				subtitle={t('operate.shared.documents.pdfPreviewFailed', {fileName})}
				hideCloseButton
				lowContrast
			/>
		);
	}

	if (state.status === 'loading') {
		return (
			<InlineLoading
				data-testid="pdf-document-preview-spinner"
				description={t('operate.shared.documents.preparingPdfPreview')}
			/>
		);
	}

	return <PreviewPdfFrame src={state.url} title={fileName} />;
};

export {PreviewPdf};
