/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Modal, Tag} from '@carbon/react';
import type {DocumentInfo} from './documentInfo';
import {toHumanReadableBytes} from './humanReadableBytes';
import {middleTruncate} from './middleTruncate';
import {PreviewDocumentButton} from './PreviewDocumentButton';
import {DownloadDocumentButton} from './DownloadDocumentButton';
import {
	DocumentFileName,
	DocumentInfoBlock,
	DocumentList,
	DocumentListItem,
	DocumentSize,
	TruncationNotice,
} from './styled';

type Props = {
	documents: DocumentInfo[];
	labelSuffix: string;
	onClose: () => void;
	loadingHint?: string;
	errorHint?: string;
};

const DocumentListModal: React.FC<Props> = ({documents, labelSuffix, onClose, loadingHint, errorHint}) => {
	const {t} = useTranslation();
	const isFullyLoaded = !loadingHint && !errorHint;
	const count = documents.length;
	const amount = isFullyLoaded ? `${count}` : `${count}+`;

	return (
		<Modal
			open
			onRequestClose={onClose}
			modalHeading={t(
				count !== 1 ? 'operate.shared.documents.listTitleMultiple' : 'operate.shared.documents.listTitleSingle',
				{amount, label: labelSuffix},
			)}
			closeButtonLabel={t('operate.shared.editors.close')}
			size="md"
			passiveModal
		>
			<DocumentList>
				{documents.map((document, index) => (
					<DocumentListItem aria-labelledby={`document-item-${index}`} key={index}>
						<DocumentInfoBlock>
							<DocumentFileName id={`document-item-${index}`} title={document.fileName}>
								{middleTruncate(document.fileName)}
							</DocumentFileName>
							<DocumentSize>{toHumanReadableBytes(document.size)}</DocumentSize>
							{document.isExpired && (
								<Tag type="red" size="sm">
									{t('operate.shared.documents.expired')}
								</Tag>
							)}
						</DocumentInfoBlock>
						<PreviewDocumentButton document={document} labelSuffix={labelSuffix} />
						<DownloadDocumentButton document={document} labelSuffix={labelSuffix} />
					</DocumentListItem>
				))}
			</DocumentList>
			{loadingHint && <TruncationNotice>{loadingHint}</TruncationNotice>}
			{errorHint && <TruncationNotice>{errorHint}</TruncationNotice>}
		</Modal>
	);
};

export {DocumentListModal};
