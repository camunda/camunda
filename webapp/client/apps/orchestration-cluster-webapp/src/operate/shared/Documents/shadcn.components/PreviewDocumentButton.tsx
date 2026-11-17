/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {Button, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Eye} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import type {DocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {DisabledActionTooltip} from './DisabledActionTooltip';
import {DocumentPreviewModal} from './DocumentPreviewModal';

type Props = {
	document: DocumentInfo;
	labelSuffix: string;
};

function getDisabledTooltipKey(document: DocumentInfo) {
	if (document.isExpired) {
		return 'operate.shared.documents.expiredTooltip';
	}
	if (document.link === null) {
		return 'operate.shared.documents.previewUnavailable';
	}
	return 'operate.shared.documents.unsupportedFileType';
}

const PreviewDocumentButton: React.FC<Props> = ({document, labelSuffix}) => {
	const {t} = useTranslation();
	const [isOpen, setIsOpen] = useState(false);
	const isDisabled = document.link === null || document.isExpired || document.type === 'unknown';
	const ariaLabel = t('operate.shared.documents.previewLabel', {label: labelSuffix});

	return (
		<>
			{isDisabled ? (
				<DisabledActionTooltip label={t(getDisabledTooltipKey(document))}>
					<Button variant="ghost" size="icon-sm" aria-label={ariaLabel} disabled>
						<Eye className="size-4" aria-hidden="true" />
					</Button>
				</DisabledActionTooltip>
			) : (
				<Tooltip>
					<TooltipTrigger asChild>
						<Button variant="ghost" size="icon-sm" aria-label={ariaLabel} onClick={() => setIsOpen(true)}>
							<Eye className="size-4" aria-hidden="true" />
						</Button>
					</TooltipTrigger>
					<TooltipContent side="top">{t('operate.shared.documents.preview')}</TooltipContent>
				</Tooltip>
			)}
			{isOpen && <DocumentPreviewModal document={document} onClose={() => setIsOpen(false)} />}
		</>
	);
};

export {PreviewDocumentButton};
