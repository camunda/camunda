/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Modal} from '@carbon/react';
import type {DocumentInfo} from './documentInfo';
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

	return <p>{t('operate.shared.documents.previewNotAvailable', {fileName})}</p>;
};

const DocumentPreviewModal: React.FC<Props> = ({document, onClose}) => {
	const {t} = useTranslation();

	return (
		<Modal
			open
			onRequestClose={onClose}
			modalHeading={t('operate.shared.documents.previewTitle', {fileName: document.fileName})}
			closeButtonLabel={t('operate.shared.editors.close')}
			size="lg"
			passiveModal
		>
			<ModalContent document={document} />
		</Modal>
	);
};

export {DocumentPreviewModal};
