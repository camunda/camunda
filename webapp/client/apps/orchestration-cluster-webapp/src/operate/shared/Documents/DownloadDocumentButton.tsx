/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {useTranslation} from 'react-i18next';
import {Button} from '@carbon/react';
import {Download} from '@carbon/react/icons';
import type {DocumentInfo} from './documentInfo';
import {DisabledActionTooltip} from './DisabledActionTooltip';

type Props = {
	document: DocumentInfo;
	labelSuffix: string;
};

const DownloadDocumentButton: React.FC<Props> = ({document, labelSuffix}) => {
	const {t} = useTranslation();
	const isDisabled = document.link === null || document.isExpired;
	const button = (
		<Button
			as={isDisabled ? undefined : 'a'}
			href={document.link ?? undefined}
			download={isDisabled ? undefined : document.fileName}
			rel="noopener"
			kind="ghost"
			size="sm"
			hasIconOnly
			renderIcon={Download}
			iconDescription={t('operate.shared.documents.download')}
			tooltipPosition="top"
			tooltipAlignment="end"
			aria-label={t('operate.shared.documents.downloadLabel', {label: labelSuffix})}
			disabled={isDisabled}
		/>
	);

	if (!isDisabled) {
		return button;
	}

	return (
		<DisabledActionTooltip
			label={t(
				document.isExpired ? 'operate.shared.documents.expiredTooltip' : 'operate.shared.documents.downloadUnavailable',
			)}
		>
			{button}
		</DisabledActionTooltip>
	);
};

export {DownloadDocumentButton};
