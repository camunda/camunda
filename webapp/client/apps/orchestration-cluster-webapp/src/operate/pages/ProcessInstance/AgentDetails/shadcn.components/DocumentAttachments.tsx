/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useId, useRef, useState} from 'react';
import {useTranslation} from 'react-i18next';
import {Heading, IconButton} from '@camunda/design-system';
import {Eye, File} from '@camunda/design-system/icons';
import type {AgentInstanceMessageContent} from '@camunda/camunda-api-zod-schemas/8.11';
import {DocumentListModal} from '#/operate/shared/Documents/shadcn.components/DocumentListModal';
import {toDocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {middleTruncate} from '#/operate/shared/Documents/middleTruncate';

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

	const documentReferences = content.flatMap((entry) =>
		entry.contentType === 'DOCUMENT' ? [entry.documentReference] : [],
	);

	if (documentReferences.length === 0) {
		return null;
	}

	const visibleDocuments = documentReferences.slice(0, MAX_VISIBLE_DOCUMENTS);
	const hiddenDocumentsCount = documentReferences.length - visibleDocuments.length;

	return (
		<div className="group/documents mt-4 inline-flex w-fit flex-wrap items-center gap-2">
			<Heading as="h3" id={id} variant="label-sm" className="text-[var(--neutral-foreground-subtle)]">
				{t('operate.processInstance.details.agent.documents')}
			</Heading>
			<ul aria-labelledby={id} className="contents list-none p-0">
				{visibleDocuments.map(({documentId, metadata}) => (
					<li
						key={documentId}
						title={metadata.fileName}
						className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-0.5 text-xs"
					>
						<File className="size-3 text-[var(--neutral-foreground-subtle)]" aria-hidden />
						{middleTruncate(metadata.fileName, FILE_NAME_TRUNCATION_LIMIT)}
					</li>
				))}
				{hiddenDocumentsCount > 0 && (
					<li className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-0.5 text-xs">
						{t('operate.processInstance.details.agent.moreDocuments', {count: hiddenDocumentsCount})}
					</li>
				)}
			</ul>
			<IconButton
				variant="ghost"
				size="sm"
				icon={Eye}
				label={t('operate.processInstance.details.agent.viewDocuments')}
				tooltipSide="top"
				className="opacity-0 transition-opacity group-hover/documents:opacity-100! focus-visible:opacity-100!"
				ref={triggerRef}
				onClick={() => setIsModalOpen(true)}
			/>
			{isModalOpen && (
				<DocumentListModal
					documents={documentReferences.map(toDocumentInfo)}
					labelSuffix={modalTitleSuffix}
					onClose={() => {
						setIsModalOpen(false);
						triggerRef.current?.focus();
					}}
				/>
			)}
		</div>
	);
};

export {DocumentAttachments};
