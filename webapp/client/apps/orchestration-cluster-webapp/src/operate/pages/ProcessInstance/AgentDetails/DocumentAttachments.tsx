/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useRef, useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Document, View} from '@carbon/react/icons';
import type {AgentInstanceMessageContent} from '@camunda/camunda-api-zod-schemas/8.11';
import {DocumentListModal} from '#/operate/shared/Documents/DocumentListModal';
import {toDocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {middleTruncate} from '#/operate/shared/Documents/middleTruncate';
import {Attachment, AttachmentsContainer, AttachmentsLabel, AttachmentsList, HiddenButton} from './attachments.styled';

const MAX_VISIBLE_DOCUMENTS = 3;
const FILE_NAME_TRUNCATION_LIMIT = 20;

type DocumentAttachmentsProps = {
	content: AgentInstanceMessageContent[];
	modalTitleSuffix: string;
};

const DocumentAttachments: React.FC<DocumentAttachmentsProps> = ({content, modalTitleSuffix}) => {
	const {t} = useTranslation();
	const id = useId();
	const [isModalOpen, setIsModalOpen] = useState(false);
	const triggerRef = useRef<HTMLButtonElement>(null);

	const documents = content.flatMap((entry) =>
		entry.contentType === 'DOCUMENT' ? [toDocumentInfo(entry.documentReference)] : [],
	);

	if (documents.length === 0) {
		return null;
	}

	const visibleDocuments = documents.slice(0, MAX_VISIBLE_DOCUMENTS);
	const hiddenDocumentsCount = documents.length - visibleDocuments.length;

	return (
		<AttachmentsContainer>
			<AttachmentsLabel id={id}>{t('operate.processInstance.details.agent.documents')}</AttachmentsLabel>
			<AttachmentsList aria-labelledby={id}>
				{visibleDocuments.map((document, index) => (
					<Attachment key={index} title={document.fileName}>
						<Document size={12} />
						{middleTruncate(document.fileName, FILE_NAME_TRUNCATION_LIMIT)}
					</Attachment>
				))}
				{hiddenDocumentsCount > 0 && (
					<Attachment>
						{t('operate.processInstance.details.agent.moreDocuments', {count: hiddenDocumentsCount})}
					</Attachment>
				)}
			</AttachmentsList>
			<HiddenButton
				kind="ghost"
				size="xs"
				hasIconOnly
				renderIcon={View}
				iconDescription={t('operate.processInstance.details.agent.viewDocuments')}
				tooltipPosition="top"
				tooltipAlignment="end"
				ref={triggerRef}
				onClick={() => setIsModalOpen(true)}
			/>
			{isModalOpen &&
				createPortal(
					<DocumentListModal
						documents={documents}
						labelSuffix={modalTitleSuffix}
						onClose={() => {
							setIsModalOpen(false);
							triggerRef.current?.focus();
						}}
					/>,
					window.document.body,
				)}
		</AttachmentsContainer>
	);
};

export {DocumentAttachments};
