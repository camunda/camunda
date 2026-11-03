/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useEffect, useState} from 'react';
import {Alert, Spinner} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';

type Props = {
	src: string;
	fileName: string;
};

const PDF_CONTENT_TYPE = 'application/pdf';

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
				if (response.headers.get('Content-Type')?.split(';')[0]?.trim().toLowerCase() !== PDF_CONTENT_TYPE) {
					throw new Error('Document is not a PDF');
				}
				url = URL.createObjectURL(new Blob([await response.arrayBuffer()], {type: PDF_CONTENT_TYPE}));
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
		return <Alert variant="destructive" description={t('operate.shared.documents.pdfPreviewFailed', {fileName})} />;
	}

	if (state.status === 'loading') {
		return <Spinner label={t('operate.shared.documents.preparingPdfPreview')} />;
	}

	return <iframe src={state.url} title={fileName} className="block h-[80vh] w-full border-0" />;
};

export {PreviewPdf};
