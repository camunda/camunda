/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */

import {Button, Tooltip, TooltipContent, TooltipTrigger} from '@camunda/design-system';
import {Download} from '@camunda/design-system/icons';
import {useTranslation} from 'react-i18next';
import type {DocumentInfo} from '#/operate/shared/Documents/documentInfo';
import {DisabledActionTooltip} from './DisabledActionTooltip';

type Props = {
	document: DocumentInfo;
	labelSuffix: string;
};

const DownloadDocumentButton: React.FC<Props> = ({document, labelSuffix}) => {
	const {t} = useTranslation();
	const isDisabled = document.link === null || document.isExpired;
	const ariaLabel = t('operate.shared.documents.downloadLabel', {label: labelSuffix});

	if (isDisabled) {
		return (
			<DisabledActionTooltip
				label={t(
					document.isExpired
						? 'operate.shared.documents.expiredTooltip'
						: 'operate.shared.documents.downloadUnavailable',
				)}
			>
				<Button variant="ghost" size="icon-sm" aria-label={ariaLabel} disabled>
					<Download className="size-4" aria-hidden="true" />
				</Button>
			</DisabledActionTooltip>
		);
	}

	return (
		<Tooltip>
			<TooltipTrigger asChild>
				<Button variant="ghost" size="icon-sm" asChild>
					<a href={document.link ?? undefined} download={document.fileName} rel="noopener" aria-label={ariaLabel}>
						<Download className="size-4" aria-hidden="true" />
					</a>
				</Button>
			</TooltipTrigger>
			<TooltipContent side="top">{t('operate.shared.documents.download')}</TooltipContent>
		</Tooltip>
	);
};

export {DownloadDocumentButton};
