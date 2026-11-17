/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useState} from 'react';
import {createPortal} from 'react-dom';
import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {View} from '@carbon/react/icons';
import type {DocumentInfo} from './documentInfo';
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
	const button = (
		<Button
			kind="ghost"
			size="sm"
			hasIconOnly
			renderIcon={View}
			iconDescription={t('operate.shared.documents.preview')}
			tooltipPosition="top"
			aria-label={t('operate.shared.documents.previewLabel', {label: labelSuffix})}
			disabled={isDisabled}
			onClick={() => setIsOpen(true)}
		/>
	);

	return (
		<>
			{isDisabled ? (
				<DisabledActionTooltip label={t(getDisabledTooltipKey(document))}>{button}</DisabledActionTooltip>
			) : (
				button
			)}
			{isOpen &&
				createPortal(
					<DocumentPreviewModal document={document} onClose={() => setIsOpen(false)} />,
					window.document.body,
				)}
		</>
	);
};

export {PreviewDocumentButton};
