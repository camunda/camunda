/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Dialog, DialogBody, DialogContent, DialogHeader, DialogTitle} from '@camunda/design-system';
import {useTranslation} from 'react-i18next';
import type {DocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {PreviewImage} from './PreviewImage';
import {PreviewJson} from './PreviewJson';
import {PreviewPdf} from './PreviewPdf';

type Props = {
	document: DocumentInfo;
	onClose: () => void;
};

const ModalContent: React.FC<{document: DocumentInfo}> = ({document}) => {
	const {t} = useTranslation();
	const {link, type, fileName} = document;

	if (link !== null && type === 'image') {
		return <PreviewImage src={link} fileName={fileName} />;
	}

	if (link !== null && type === 'pdf') {
		return <PreviewPdf src={link} fileName={fileName} />;
	}

	if (link !== null && type === 'json') {
		return <PreviewJson src={link} fileName={fileName} />;
	}

	return <p className="text-sm">{t('operate.shared.documents.previewNotAvailable', {fileName})}</p>;
};

const DocumentPreviewModal: React.FC<Props> = ({document, onClose}) => {
	const {t} = useTranslation();

	return (
		<Dialog
			open
			onOpenChange={(open) => {
				if (!open) {
					onClose();
				}
			}}
		>
			<DialogContent size="lg" aria-describedby={undefined} closeLabel={t('operate.shared.editors.close')}>
				<DialogHeader>
					<DialogTitle>{t('operate.shared.documents.previewTitle', {fileName: document.fileName})}</DialogTitle>
				</DialogHeader>
				<DialogBody>
					<ModalContent document={document} />
				</DialogBody>
			</DialogContent>
		</Dialog>
	);
};

export {DocumentPreviewModal};
